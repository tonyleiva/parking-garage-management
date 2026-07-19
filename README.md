# Gerenciamento de estacionamento

Backend responsável por inicializar e gerenciar a operação de uma garagem, processando de forma transacional os eventos de entrada, estacionamento e saída enviados pelo simulador.

## Escopo atual

Esta etapa implementa a inicialização controlada da garagem, o webhook dos eventos `ENTRY`, `PARKED` e `EXIT` e a consulta diária de receita, incluindo idempotência, concorrência, preço dinâmico e cálculo do valor da permanência.

## Stack

- Java 21
- Spring Boot 4.1
- Gradle com Groovy DSL
- MySQL 8.4
- Spring Data JPA
- Flyway
- Docker Compose
- JUnit 5
- Mockito
- Testcontainers

## Qualidade e integração contínua

O GitHub Actions executa os testes automatizados, gera os relatórios de cobertura com o JaCoCo e envia a análise ao SonarQube Cloud. A cobertura é importada pelo relatório XML do JaCoCo, e o Quality Gate pode impedir o merge quando os critérios de qualidade não forem atendidos. O `SONAR_TOKEN` permanece armazenado como secret no GitHub e não é incluído no repositório.

## Arquitetura

O projeto utiliza uma arquitetura em camadas inspirada em Clean Architecture e Arquitetura Hexagonal, separando regras de domínio, orquestração da aplicação, integrações de infraestrutura e apresentação HTTP, sem adicionar abstrações desnecessárias para o tamanho da solução.

- `domain`: entidades e regras centrais de setores, vagas, sessões, preço e cobrança;
- `application`: validação, inicialização e orquestração dos eventos;
- `infrastructure`: cliente HTTP, persistência, banco de dados e configurações;
- `presentation`: controller, DTOs e tratamento global de erros da API HTTP.

As entidades de domínio também são entidades JPA. Não existe uma segunda representação nem mapeadores sem benefício concreto.

## Pré-requisitos

- Java 21;
- Docker Desktop ou Docker Engine com Docker Compose;
- Git.

Não é necessário instalar o Gradle, pois o projeto utiliza Gradle Wrapper.

No Docker Desktop para macOS, o recurso de *host networking* deve estar habilitado para executar o simulador com `--network="host"`.

## Executando o ambiente local

### 1. Subir o MySQL

Na raiz do projeto:

```bash
docker compose up -d mysql
docker compose ps
```

Por padrão, o banco fica disponível com as seguintes configurações:

```text
Host: localhost
Porta: 3306
Banco: parking_garage
Usuário: parking_user
Senha: parking_password
```

A porta pode ser alterada:

```bash
MYSQL_PORT=3307 docker compose up -d mysql
```

As credenciais podem ser configuradas pelas variáveis:

```text
MYSQL_DATABASE
MYSQL_USER
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
```

O volume `mysql-data` preserva os dados entre reinicializações.

### 2. Criar e iniciar o simulador

Na primeira execução, crie o container:

```bash
docker run -d \
  --name garage-sim \
  --network="host" \
  cfontes0estapar/garage-sim:1.0.0
```

Nas execuções seguintes, basta iniciar o container já existente:

```bash
docker start garage-sim
```

Verifique se o simulador está disponível:

```bash
curl http://localhost:3000/garage
```

Para acompanhar seus logs:

```bash
docker logs -f garage-sim
```

O simulador é externo ao projeto e expõe, por padrão:

```text
GET http://localhost:3000/garage
```

A URL pode ser alterada pela variável `GARAGE_SIMULATOR_BASE_URL` ou pela propriedade `garage.simulator.base-url`.

O simulador precisa estar disponível quando o banco estiver vazio ou quando o reset explícito estiver habilitado. Com estado persistido e reset desabilitado, a aplicação continua pelo banco sem consultá-lo.

### 3. Iniciar a aplicação

Com o MySQL em execução e o simulador disponível quando necessário para a carga inicial ou para o reset explícito:

```bash
./gradlew bootRun
```

A aplicação executa na porta `3003`. Na primeira execução, consulta o simulador, valida o snapshot e realiza a carga inicial. Nas execuções seguintes, por padrão, retoma integralmente o estado do MySQL.

Para habilitar logs detalhados do projeto:

```bash
LOGGING_LEVEL_COM_TONYLEIVA_PARKINGGARAGE=DEBUG ./gradlew bootRun
```

Para apagar o cenário operacional e carregar integralmente um novo snapshot validado:

```bash
GARAGE_STARTUP_RESET_FROM_SIMULATOR=true ./gradlew bootRun
```

### 4. Conferir a inicialização

É possível acessar o banco pelo DBeaver ou por outro cliente MySQL com as credenciais descritas anteriormente.

Consultas úteis:

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

Com o snapshot atual do simulador, o resultado esperado é:

- 2 setores;
- 30 vagas;
- horários comerciais iguais aos valores retornados pelo simulador;
- estado de ocupação igual ao snapshot recebido;
- migrations V1, V2 e V3 registradas no histórico do Flyway.

## Encerrando o ambiente

Para parar a aplicação, pressione `Ctrl + C` no terminal onde o `bootRun` está em execução.

Para parar os containers:

```bash
docker stop garage-sim
docker compose stop mysql
```

Para remover o container do simulador:

```bash
docker rm garage-sim
```

Para remover os containers e a rede criados pelo Compose:

```bash
docker compose down
```

O comando anterior preserva o volume nomeado do MySQL. Para apagar também os dados persistidos:

```bash
docker compose down -v
```

## Configuração da aplicação

Variáveis disponíveis:

| Variável | Valor padrão | Descrição |
|---|---|---|
| `DATABASE_URL` | `jdbc:mysql://localhost:3306/parking_garage` | URL JDBC do MySQL |
| `DATABASE_USERNAME` | `parking_user` | Usuário do banco |
| `DATABASE_PASSWORD` | `parking_password` | Senha do banco |
| `GARAGE_SIMULATOR_BASE_URL` | `http://localhost:3000` | URL base do simulador |
| `GARAGE_SIMULATOR_CONNECT_TIMEOUT` | `2s` | Timeout de conexão |
| `GARAGE_SIMULATOR_READ_TIMEOUT` | `5s` | Timeout de leitura |
| `GARAGE_STARTUP_RESET_FROM_SIMULATOR` | `false` | Apaga o cenário atual e carrega um novo snapshot validado |

Configuração equivalente:

```yaml
garage:
  startup:
    reset-from-simulator: false
```

## Estratégia de startup

Existe uma única propriedade controlando a origem do estado inicial. A antiga propriedade `garage.synchronization.enabled` foi removida para evitar comportamentos concorrentes.

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

No reset, o snapshot é buscado e validado antes de qualquer exclusão. A remoção explícita e a nova carga acontecem na mesma transação, na ordem eventos processados → sessões → vagas → setores. Falhas provocam rollback integral; não são usadas cascatas destrutivas.

Ao final, um log `INFO` informa origem (`SIMULADOR` ou `BANCO_DE_DADOS`), setores, vagas ocupadas e livres. Em `DEBUG`, cada setor recebe um resumo com capacidade, ocupação, preço-base e horários. Vagas individuais não são registradas em `INFO`.

## Endpoints expostos

A aplicação expõe atualmente:

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/webhook` | Recebe eventos `ENTRY`, `PARKED` e `EXIT` |
| `GET` | `/revenue` | Consulta a receita diária de um setor pelo dia do `EXIT` |

O endpoint `GET /garage` pertence ao simulador externo, disponível por padrão em `http://localhost:3000/garage`.

O endpoint `/revenue` ainda não foi implementado.

## Webhook

O simulador envia eventos para:

```text
POST http://localhost:3003/webhook
```

### ENTRY

```json
{
  "license_plate": "ZUL0001",
  "entry_time": "2025-01-01T12:00:00.000Z",
  "event_type": "ENTRY"
}
```

Cria uma sessão `ENTERED` sem setor, vaga ou preço. O horário recebido é preservado. A capacidade comprometida é `vagas ocupadas + sessões ENTERED`; sessões `PARKED` não são somadas novamente.

### PARKED

```json
{
  "license_plate": "ZUL0001",
  "lat": -23.561684,
  "lng": -46.655981,
  "event_type": "PARKED"
}
```

Localiza a vaga pelo valor numérico das coordenadas informadas, associa setor e vaga, ocupa a vaga e muda a sessão para `PARKED`. Como o contrato não fornece horário, `parkedAt` usa um `Clock` UTC injetável e tem finalidade operacional; a cobrança começa em `entryTime`.

As coordenadas são armazenadas como valores decimais de escala fixa. Zeros finais não alteram seu valor numérico; por exemplo, `-46.65590100` e `-46.655901` representam a mesma coordenada.

### EXIT

```json
{
  "license_plate": "ZUL0001",
  "exit_time": "2025-01-01T13:10:00.000Z",
  "event_type": "EXIT"
}
```

Libera a vaga, calcula e persiste `amount` e finaliza a sessão.

### Exemplos com curl

```bash
curl -i -X POST http://localhost:3003/webhook \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: entry-zul0001-20250101' \
  -d '{"license_plate":"ZUL0001","entry_time":"2025-01-01T12:00:00.000Z","event_type":"ENTRY"}'

curl -i -X POST http://localhost:3003/webhook \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: parked-zul0001-20250101' \
  -d '{"license_plate":"ZUL0001","lat":-23.561684,"lng":-46.655981,"event_type":"PARKED"}'

curl -i -X POST http://localhost:3003/webhook \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: exit-zul0001-20250101' \
  -d '{"license_plate":"ZUL0001","exit_time":"2025-01-01T13:10:00.000Z","event_type":"EXIT"}'
```

## Processamento e estados

O dispatcher usa Strategy e seleciona `EntryEventHandler`, `ParkedEventHandler` ou `ExitEventHandler` pelo `event_type`. Handlers duplicados são detectados na inicialização. O ciclo permitido é:

```text
ENTERED → PARKED → FINISHED
```

Não são criadas sessões `REJECTED`. Rejeições de negócio são auditadas em `processed_webhook_event` junto com status HTTP e mensagem.

## Respostas HTTP

| Situação | HTTP | `status` |
|---|---:|---|
| Processado | 200 | `PROCESSED` |
| Duplicado compatível | mesmo HTTP e `status` originais | `PROCESSED` ou `REJECTED`, com `duplicate: true` |
| Payload inválido | 400 | `INVALID` |
| Recurso inexistente | 404 | `REJECTED` |
| Conflito de negócio | 409 | `REJECTED` |
| Erro inesperado | 500 | `ERROR` |

Uma resposta de evento também contém `duplicate`, permitindo distinguir uma repetição sem mudar o resultado original. Detalhes internos, SQL e stack traces não são expostos.

## Preço dinâmico e cobrança

O preço é calculado antes de ocupar a nova vaga e congelado na sessão:

| Ocupação anterior | Multiplicador |
|---|---:|
| `0% <= ocupação < 25%` | `0.90` |
| `25% <= ocupação < 50%` | `1.00` |
| `50% <= ocupação < 75%` | `1.10` |
| `75% <= ocupação < 100%` | `1.25` |
| `100%` | setor lotado |

Multiplicadores usam escala 2; preços e valores usam escala 4 e `HALF_UP`. Até 30 minutos, inclusive, o valor é zero. Depois disso, cobra-se desde a primeira hora, usando o teto da duração total: 31 minutos custam uma hora, 60 minutos custam uma hora, `60:00.001` custa duas horas e 70 minutos custam duas horas.

## Consulta de receita

Por compatibilidade com o contrato do desafio, `GET /revenue` recebe um body JSON:

```json
{
  "date": "2025-01-01",
  "sector": "A"
}
```

A receita soma os valores persistidos no `EXIT` das sessões finalizadas do setor. A data representa o dia do `EXIT` em `America/Sao_Paulo`; o retorno usa `BRL` e inclui o instante UTC da consulta.

Valores financeiros são mantidos internamente com quatro casas decimais. O faturamento soma os valores persistidos com sua precisão original e somente o total apresentado pela API é arredondado para duas casas com `HALF_UP`. Essa decisão atende às duas casas exigidas na resposta pelo enunciado, que não define o tratamento de frações de centavo.

No fluxo operacional, `ENTRY` consome capacidade global sem ocupar uma vaga específica. `PARKED` define a vaga e o setor e congela o preço dinâmico. A cobrança considera todo o intervalo entre `ENTRY` e `EXIT`.

## Idempotência

O header opcional `Idempotency-Key` é respeitado exatamente como enviado, aplicando-se somente `trim` e a validação de conteúdo não vazio. O valor normalizado é a chave lógica, sem prefixo de tipo, placa ou outros dados. Seu hash SHA-256 permanece protegido por constraint única.

A mesma chave fornecida pelo produtor representa uma única requisição lógica, independentemente do tipo do evento:

- mesma chave e mesmo payload canônico: retorna o resultado HTTP e a mensagem originais sem reaplicar efeitos;
- mesma chave e payload diferente: retorna `409 Conflict`;
- mesma chave e `event_type` diferente: retorna `409 Conflict`;
- eventos diferentes devem usar chaves diferentes.

Além do hash da chave, cada evento armazena um fingerprint SHA-256 do request canônico. O fingerprint contém placa normalizada, `event_type` e somente os campos relevantes daquele evento. Uma tentativa conflitante não substitui nem altera o registro original.

Sem o header, são usadas as chaves determinísticas:

```text
ENTRY|licensePlate|entryTime
PARKED|licensePlate|sessionEntryTime|latitude|longitude
PARKED|licensePlate|NO_ACTIVE_SESSION|latitude|longitude
EXIT|licensePlate|exitTime
```

A placa é convertida para maiúsculas, instantes usam `Instant.toString()` e coordenadas são normalizadas sem zeros finais. No `PARKED`, uma sessão ativa inclui seu `sessionEntryTime`, evitando colisões entre permanências distintas na mesma vaga. Sem sessão ativa, o marcador semântico `NO_ACTIVE_SESSION` identifica a rejeição sem criar um horário fictício. Uma sessão futura válida gera uma chave diferente.

Evento, resultado e alteração operacional são confirmados na mesma transação. A constraint única protege também contra requisições concorrentes. O header continua sendo a forma mais robusta de idempotência porque o contrato do simulador não fornece `event_id`; as chaves determinísticas são o fallback da aplicação.

## Concorrência e locks

São usados locks `PESSIMISTIC_WRITE` específicos. No `PARKED`, a ordem é sessão → vaga → setor. O lock do setor serializa estacionamentos simultâneos em vagas diferentes e garante que o segundo preço observe a ocupação confirmada pelo primeiro.

No `ENTRY`, os setores são bloqueados por ID para serializar a capacidade global. As transações de `ENTRY` e `PARKED` usam localmente `READ_COMMITTED`, pois suas contagens precisam observar commits ocorridos enquanto aguardavam os locks; o isolamento global não é alterado. As transações permanecem curtas.

## Validação fail-fast

A configuração completa é validada antes da primeira escrita no banco.

Entre as validações realizadas estão:

- listas de setores e vagas obrigatórias e não vazias;
- setores duplicados;
- identificadores externos de vagas duplicados ou não positivos;
- coordenadas duplicadas;
- vagas associadas a setores inexistentes;
- preço-base negativo;
- capacidade não positiva;
- duração máxima não positiva;
- horários inválidos;
- divergência entre `max_capacity` e a quantidade de vagas do setor.

Uma configuração inválida interrompe a inicialização com mensagem operacional em português. Erros de integração e parsing preservam a causa técnica original.

## Flyway

O Flyway aplica migrations SQL explícitas durante a inicialização.

O Hibernate utiliza `ddl-auto: validate`: ele valida o schema criado pelo Flyway, mas não cria nem altera tabelas automaticamente.

A migration inicial está em:

```text
src/main/resources/db/migration/V1__create_garage_tables.sql
```

A segunda migration cria sessões e o registro idempotente de eventos:

```text
src/main/resources/db/migration/V2__create_parking_sessions_and_webhook_events.sql
```

A terceira migration adiciona o fingerprint canônico do request aos eventos processados:

```text
src/main/resources/db/migration/V3__add_webhook_request_fingerprint.sql
```

## Datas, horários e logs

Timestamps absolutos, como `created_at` e `updated_at`, são representados por `Instant` e tratados em UTC.

Horários comerciais, como abertura e fechamento dos setores, são representados por `LocalTime` e armazenados exatamente como horários locais, sem conversão de fuso horário.

Os logs de inicialização seguem o resumo descrito na estratégia de startup. O processamento não registra payloads completos nem credenciais.

## Testes e build

Os testes unitários e do cliente HTTP não dependem do simulador real.

Os testes de integração utilizam MySQL com Testcontainers e, portanto, exigem que o Docker esteja em execução.

Executar os testes:

```bash
./gradlew clean test
```

Executar o build completo:

```bash
./gradlew build
```

## Validação manual sugerida

Uma sequência mínima para validar o fluxo é:

1. enviar `ENTRY`;
2. repetir o mesmo request e confirmar `duplicate: true`;
3. reutilizar a mesma chave com payload diferente e confirmar `409`;
4. enviar `PARKED` para uma vaga livre;
5. repetir o `PARKED`;
6. enviar `EXIT`;
7. repetir o `EXIT`;
8. consultar `parking_session` e `processed_webhook_event`.

Ao final, a sessão deve estar em `FINISHED`, a vaga deve estar livre e as duplicidades não devem gerar novos efeitos de domínio.

## Premissas e decisões técnicas

1. `open_hour`, `close_hour` e `duration_limit_minutes` são persistidos, mas ainda não influenciam o processamento porque o enunciado não define as regras associadas.
2. Em um sistema produtivo, seria necessário alinhar com a área de negócio:
   - impedimento ou sinalização de estacionamento fora do horário do setor;
   - tratamento de permanências acima do limite;
   - alertas operacionais;
   - eventual cobrança adicional;
   - eventual abertura de ocorrência.
3. A configuração é rejeitada se `max_capacity` não corresponder à quantidade de vagas recebidas para o setor.
4. A validação integral ocorre antes da persistência.
5. Uma configuração inválida interrompe a inicialização.
6. Identificadores e código são escritos em inglês; logs, mensagens de erro e respostas operacionais são escritos em português.
7. O simulador é a fonte da verdade apenas na carga inicial ou no reset explícito. No funcionamento padrão com banco preenchido, o banco preserva integralmente o estado operacional.
8. Timestamps absolutos são tratados em UTC, enquanto horários comerciais não sofrem conversão de fuso.

### Premissas sobre ENTRY e PARKED

O evento `ENTRY` informa apenas a placa e o horário de entrada, sem setor, vaga ou coordenadas.

Por esse motivo:

- a entrada já é contabilizada na capacidade total da garagem;
- nenhuma vaga específica é ocupada nesse momento;
- a vaga e o setor são definidos no evento `PARKED`, quando as coordenadas passam a estar disponíveis;
- o preço dinâmico é congelado no `PARKED`, primeiro momento em que o setor pode ser identificado;
- o cálculo da cobrança utiliza todo o período entre `ENTRY` e `EXIT`.

Essa decisão preserva a regra de cobrança desde a entrada e evita inventar uma estratégia de seleção de setor não definida no enunciado.

## Evoluções futuras

### Contrato HTTP da receita

Uma evolução poderá mover `date` e `sector` para query parameters. Embora preservado nesta entrega por compatibilidade com o desafio, um body em `GET` não possui semântica geralmente definida no padrão HTTP e pode ter suporte inconsistente entre clientes, proxies e caches.

### Configuração do preço dinâmico

As faixas de ocupação e seus respectivos multiplicadores poderão ser armazenados no banco de dados, permitindo ajustes de negócio sem alteração de código e sem uma nova implantação da aplicação.

### Estratégia de sincronização

A estratégia atual diferencia carga inicial, retomada do banco e reset explícito. Evoluções futuras podem incluir versionamento de snapshots, auditoria administrativa do reset ou reconciliação seletiva de configuração sem alterar o estado operacional.

### Regras de horário e permanência

Após alinhamento com a área de negócio, poderão ser implementados:

- bloqueio ou sinalização de estacionamento fora do horário do setor;
- tratamento de veículos acima de `duration_limit_minutes`;
- alertas, cobranças adicionais ou abertura de ocorrências.

### Saída sem estacionamento confirmado

Atualmente, o fluxo exige a sequência:

`ENTRY → PARKED → EXIT`

Foi identificado o cenário em que um veículo entra na garagem, não encontra vaga no setor desejado, por exemplo, e sai sem receber um evento `PARKED`.

A capacidade global já é comprometida no momento do `ENTRY`. Entretanto, esse fluxo alternativo não foi implementado porque o enunciado não define:

- se a saída direta deve ser permitida;
- se deve haver gratuidade;
- qual seria o tempo máximo de tolerância;
- se deve existir cobrança após esse período;
- em qual condição a sessão deve ser finalizada e a capacidade global liberada.

Antes de permitir `EXIT` diretamente após `ENTRY`, essa regra deveria ser validada com a área de negócio.

Uma possível evolução seria permitir `ENTRY → EXIT` dentro de uma janela de tolerância, finalizando a sessão, liberando a capacidade global e não gerando cobrança.
