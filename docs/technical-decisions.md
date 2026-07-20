# Decisões técnicas

Este documento complementa o [README](../README.md) principal com as decisões arquiteturais, premissas de negócio e evoluções consideradas durante a implementação.

## Arquitetura

O projeto utiliza uma arquitetura em camadas inspirada em Clean Architecture e Arquitetura Hexagonal.

- `domain`: entidades e regras centrais de setores, vagas, sessões, preço e cobrança;
- `application`: validação, inicialização, orquestração dos eventos e casos de uso;
- `infrastructure`: cliente HTTP, persistência, banco de dados e configurações;
- `presentation`: controllers, DTOs e tratamento global de erros.

As entidades de domínio também são entidades JPA. Não existe uma segunda representação nem mapeadores sem benefício concreto para o tamanho da solução.

## Estratégia de startup

A propriedade `garage.startup.reset-from-simulator` controla a origem do estado inicial.

```text
reset=true
→ consultar e validar completamente o snapshot
→ apagar eventos, sessões, vagas e setores
→ carregar integralmente o novo snapshot

reset=false + banco vazio
→ consultar e validar o snapshot
→ realizar a carga inicial

reset=false + banco preenchido
→ não consultar o simulador
→ continuar integralmente a partir do banco
```

O snapshot é buscado e validado antes de qualquer exclusão.

A remoção e a nova carga acontecem na mesma transação, na ordem:

```text
eventos processados → sessões → vagas → setores
```

Falhas provocam rollback integral.

## Validação fail-fast

A configuração é validada antes da primeira escrita no banco.

São verificados:

- listas obrigatórias e não vazias;
- setores duplicados;
- vagas duplicadas ou com ID inválido;
- coordenadas duplicadas;
- vagas associadas a setores inexistentes;
- preço-base negativo;
- capacidade não positiva;
- duração máxima não positiva;
- horários inválidos;
- divergência entre `max_capacity` e quantidade de vagas.

Uma configuração inválida interrompe a inicialização com mensagem operacional em português.

## ENTRY, PARKED e capacidade global

O evento `ENTRY` informa apenas placa e horário de entrada, sem setor, vaga ou coordenadas.

Por esse motivo:

- a entrada já compromete a capacidade global;
- nenhuma vaga específica é ocupada no `ENTRY`;
- vaga e setor são definidos no `PARKED`;
- o preço dinâmico é congelado no `PARKED`;
- a cobrança usa todo o intervalo entre `ENTRY` e `EXIT`.

A capacidade comprometida é calculada como:

```text
vagas ocupadas + sessões ENTERED
```

Sessões `PARKED` não são contadas novamente, porque já correspondem a vagas ocupadas.

## Preço dinâmico

No `PARKED`, o setor é identificado pelas coordenadas da vaga.

O preço é calculado com base na ocupação confirmada anterior à nova ocupação e congelado antes de a vaga ser marcada como ocupada.

| Ocupação anterior | Multiplicador |
|---|---:|
| `0% <= ocupação < 25%` | `0.90` |
| `25% <= ocupação < 50%` | `1.00` |
| `50% <= ocupação < 75%` | `1.10` |
| `75% <= ocupação < 100%` | `1.25` |
| `100%` | setor lotado |

Essa escolha é necessária porque o `ENTRY` não informa o setor. Reservar um setor nessa etapa exigiria inventar uma estratégia não definida no enunciado.

## Cobrança e precisão financeira

Até 30 minutos, inclusive, o valor é zero.

Acima disso, a duração total é arredondada para cima:

- 31 minutos: uma hora;
- 60 minutos: uma hora;
- `60:00.001`: duas horas;
- 70 minutos: duas horas.

A duração é calculada entre `ENTRY` e `EXIT`.

Preços e valores são mantidos internamente com escala 4 e `HALF_UP`. O banco utiliza `DECIMAL(19,4)`.

A consulta de receita soma os valores persistidos com precisão completa. Somente o total apresentado pela API é arredondado para duas casas com `HALF_UP`.

Exemplo:

```text
50.6250 + 50.6250 = 101.2500
Resposta: 101.25
```

O arredondamento não é aplicado individualmente antes da soma.

## Consulta de receita e timezone

O endpoint `GET /revenue` segue o contrato do desafio e recebe body JSON.

A data informada representa o dia do `EXIT` em `America/Sao_Paulo`.

O serviço constrói:

- início inclusivo às `00:00`;
- fim exclusivo às `00:00` do dia seguinte.

Os dois limites são convertidos para `Instant` e utilizados na consulta:

```text
exitTime >= start
exitTime < end
```

A resposta usa timestamp UTC da consulta.

## GET com body

O contrato do desafio define `GET /revenue` com body.

A implementação preserva esse formato por compatibilidade. Entretanto, conteúdo em requisições GET não possui semântica geralmente definida no padrão HTTP e pode ter suporte inconsistente entre clientes, proxies e caches.

Uma evolução natural seria:

```text
GET /revenue?date=2026-07-18&sector=A
```

## Idempotência

O header opcional `Idempotency-Key` é aplicado apenas ao webhook.

O valor informado é normalizado com `trim`, convertido em hash SHA-256 e protegido por constraint única.

Regras:

- mesma chave e mesmo payload canônico: retorna o resultado original sem reaplicar efeitos;
- mesma chave e payload diferente: `409 Conflict`;
- mesma chave e `event_type` diferente: `409 Conflict`;
- eventos diferentes devem usar chaves diferentes.

Além do hash da chave, é persistido um fingerprint SHA-256 do request canônico.

Sem o header, são usadas chaves determinísticas:

```text
ENTRY|licensePlate|entryTime
PARKED|licensePlate|sessionEntryTime|latitude|longitude
PARKED|licensePlate|NO_ACTIVE_SESSION|latitude|longitude
EXIT|licensePlate|exitTime
```

O evento, o resultado e a alteração operacional são confirmados na mesma transação.

## Concorrência e locks

São usados locks `PESSIMISTIC_WRITE`.

No `PARKED`, a ordem de lock é:

```text
sessão → vaga → setor
```

O lock do setor serializa estacionamentos simultâneos em vagas diferentes e garante que o segundo preço observe a ocupação confirmada pelo primeiro.

No `ENTRY`, todos os setores são bloqueados por ID para serializar a capacidade global.

As transações de `ENTRY` e `PARKED` usam localmente `READ_COMMITTED`, pois suas contagens precisam observar commits realizados enquanto aguardavam locks.

## Estados e respostas

O ciclo permitido é:

```text
ENTERED → PARKED → FINISHED
```

Não são criadas sessões `REJECTED`. Rejeições de negócio são auditadas em `processed_webhook_event`.

O campo `duplicate` existe apenas nas respostas relacionadas aos webhooks. Erros genéricos e respostas de `/revenue` não possuem esse campo.

## Flyway e persistência

O Flyway aplica migrations SQL explícitas:

- `V1__create_garage_tables.sql`
- `V2__create_parking_sessions_and_webhook_events.sql`
- `V3__add_webhook_request_fingerprint.sql`

O Hibernate utiliza `ddl-auto: validate`, validando o schema sem criar ou alterar tabelas.

Timestamps absolutos são representados por `Instant` e tratados em UTC.

Horários comerciais são representados por `LocalTime` e armazenados sem conversão de fuso.

### Consultas úteis para validação

```sql
SELECT *
FROM garage_sector
ORDER BY code;

SELECT *
FROM parking_spot
ORDER BY external_id;

SELECT *
FROM parking_session
ORDER BY id;

SELECT *
FROM processed_webhook_event
ORDER BY id;

SELECT *
FROM flyway_schema_history
ORDER BY installed_rank;
```

Com o snapshot atual do simulador, a carga inicial deve apresentar:

- 2 setores;
- 30 vagas;
- horários comerciais iguais aos recebidos;
- ocupação correspondente ao snapshot;
- migrations V1, V2 e V3 registradas pelo Flyway.

## Logs

Logs e mensagens operacionais são escritos em português.

O processamento não registra payloads completos nem credenciais.

Na inicialização:

- `INFO`: origem do estado, setores, vagas ocupadas e livres;
- `DEBUG`: resumo por setor com capacidade, ocupação, preço-base e horários.

## Premissas de negócio

- `open_hour`, `close_hour` e `duration_limit_minutes` são persistidos, mas não influenciam o processamento porque o enunciado não define regras associadas;
- `max_capacity` deve corresponder à quantidade de vagas recebidas;
- o simulador é fonte da verdade apenas na carga inicial ou no reset explícito;
- com banco preenchido e reset desabilitado, o estado operacional é retomado do banco;
- código e identificadores estão em inglês;
- logs, erros e respostas operacionais estão em português.

## Evoluções futuras

### Regras de horário e permanência

Após alinhamento com a área de negócio:

- bloqueio ou sinalização fora do horário;
- tratamento de veículos acima de `duration_limit_minutes`;
- alertas;
- cobrança adicional;
- abertura de ocorrência.

### Saída sem PARKED

Atualmente o fluxo exige:

```text
ENTRY → PARKED → EXIT
```

Foi identificado o cenário em que o veículo entra, não encontra vaga ou decide deixar a garagem sem receber `PARKED`.

A capacidade global já foi comprometida no `ENTRY`, mas o enunciado não define:

- se a saída direta deve ser permitida;
- se deve haver gratuidade;
- qual o tempo de tolerância;
- se deve haver cobrança após a tolerância;
- em qual condição a sessão deve ser finalizada.

Essa regra deveria ser validada com a área de negócio antes de permitir `ENTRY → EXIT`.

### Configuração do preço dinâmico

As faixas e multiplicadores podem ser movidos para o banco, permitindo ajustes sem alteração de código.

### Estratégia de sincronização

Possíveis evoluções:

- versionamento de snapshots;
- auditoria administrativa do reset;
- reconciliação seletiva de configuração.

### Containerização da aplicação

Uma evolução poderá incluir `Dockerfile` multi-stage e execução da aplicação pelo Docker Compose.

Nesta entrega, apenas o MySQL utiliza Compose e a aplicação é iniciada pelo Gradle Wrapper, reduzindo a complexidade da integração com o simulador externo.