# Gerenciamento de estacionamento
[![CI](https://github.com/tonyleiva/parking-garage-management/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/tonyleiva/parking-garage-management/actions/workflows/ci.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=tonyleiva_parking-garage-management&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=tonyleiva_parking-garage-management)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=tonyleiva_parking-garage-management&metric=coverage)](https://sonarcloud.io/summary/new_code?id=tonyleiva_parking-garage-management)
![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)

Sistema backend para gerenciamento de estacionamento, responsável por sincronizar a configuração da garagem, processar eventos de veículos de forma transacional e consultar o faturamento diário por setor.

A solução prioriza consistência transacional, idempotência, controle de concorrência, testes automatizados e decisões explícitas para as ambiguidades do enunciado.

## Funcionalidades

- carga inicial e reset controlado da configuração da garagem;
- processamento dos eventos `ENTRY`, `PARKED` e `EXIT`;
- controle global de capacidade;
- ocupação e liberação de vagas;
- preço dinâmico por ocupação;
- cálculo e persistência da cobrança;
- idempotência com detecção de conflito de payload;
- consulta diária de receita por setor;
- proteção contra concorrência com locks pessimistas;
- migrations com Flyway;
- testes unitários e de integração com MySQL real via Testcontainers;
- integração contínua com GitHub Actions, JaCoCo e SonarQube Cloud.

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
- JaCoCo
- SonarQube Cloud
- GitHub Actions

## Qualidade e integração contínua

O GitHub Actions executa os testes automatizados, gera os relatórios de cobertura com JaCoCo e envia a análise ao SonarQube Cloud.

O Quality Gate está configurado para impedir o merge quando os critérios de qualidade não forem atendidos. O `SONAR_TOKEN` permanece armazenado como secret no GitHub e não é incluído no repositório.

Resultados atuais na branch `main`:

- 84 testes automatizados aprovados;
- Quality Gate aprovado;
- cobertura de 82,43%;
- duplicação de código de 0,0%;
- 0 issues de Security;
- 0 issues de Reliability.

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

Nas execuções seguintes:

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

O simulador expõe, por padrão:

```text
GET http://localhost:3000/garage
```

A URL pode ser alterada pela variável `GARAGE_SIMULATOR_BASE_URL` ou pela propriedade `garage.simulator.base-url`.

O simulador precisa estar disponível quando o banco estiver vazio ou quando o reset explícito estiver habilitado. Com estado persistido e reset desabilitado, a aplicação continua pelo banco sem consultá-lo.

### 3. Iniciar a aplicação

Com o MySQL em execução e o simulador disponível quando necessário:

```bash
./gradlew bootRun
```

A aplicação executa na porta `3003`.

Na primeira execução, consulta o simulador, valida o snapshot e realiza a carga inicial. Nas execuções seguintes, por padrão, retoma integralmente o estado persistido no MySQL.

Para habilitar logs detalhados:

```bash
LOGGING_LEVEL_COM_TONYLEIVA_PARKINGGARAGE=DEBUG ./gradlew bootRun
```

Para apagar o cenário operacional e carregar integralmente um novo snapshot validado:

```bash
GARAGE_STARTUP_RESET_FROM_SIMULATOR=true ./gradlew bootRun
```

### 4. Conferir a inicialização

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

## Endpoints

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/webhook` | Recebe eventos `ENTRY`, `PARKED` e `EXIT` |
| `GET` | `/revenue` | Consulta a receita diária de um setor pelo dia do `EXIT` |

O endpoint `GET /garage` pertence ao simulador externo e está disponível, por padrão, em `http://localhost:3000/garage`.

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

O evento cria uma sessão `ENTERED` sem setor, vaga ou preço.

A capacidade global já é comprometida no `ENTRY`, mesmo sem vaga específica conhecida. O horário recebido é preservado para o cálculo posterior da cobrança.

### PARKED

```json
{
  "license_plate": "ZUL0001",
  "lat": -23.561684,
  "lng": -46.655981,
  "event_type": "PARKED"
}
```

O evento localiza a vaga pelas coordenadas, associa setor e vaga, ocupa a vaga e altera a sessão para `PARKED`.

Como o contrato não fornece horário no `PARKED`, `parkedAt` usa um `Clock` UTC injetável para finalidade operacional. A cobrança continua sendo calculada desde `entryTime`.

As coordenadas são armazenadas como valores decimais de escala fixa. Zeros finais não alteram seu valor numérico; por exemplo, `-46.65590100` e `-46.655901` representam a mesma coordenada.

### EXIT

```json
{
  "license_plate": "ZUL0001",
  "exit_time": "2025-01-01T13:10:00.000Z",
  "event_type": "EXIT"
}
```

O evento libera a vaga, calcula e persiste `amount` e finaliza a sessão.

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

## Consulta de receita

Por compatibilidade com o contrato do desafio, `GET /revenue` recebe um body JSON:

```json
{
  "date": "2026-07-18",
  "sector": "A"
}
```

Exemplo:

```bash
curl --location --request GET 'http://localhost:3003/revenue' \
  --header 'Content-Type: application/json' \
  --data '{
    "date": "2026-07-18",
    "sector": "A"
  }'
```

Resposta:

```json
{
  "amount": 89.10,
  "currency": "BRL",
  "timestamp": "2026-07-19T17:52:24.162Z"
}
```

A consulta:

- soma apenas sessões `FINISHED`;
- filtra pelo setor informado;
- usa o dia do `EXIT`;
- interpreta a data em `America/Sao_Paulo`;
- considera início inclusivo e fim exclusivo;
- soma diretamente os valores persistidos no banco;
- retorna `0.00` quando não há faturamento;
- não recalcula preço, duração ou tarifa.

Valores financeiros são mantidos internamente com quatro casas decimais. O faturamento soma os valores persistidos com sua precisão original e somente o total apresentado pela API é arredondado para duas casas com `HALF_UP`.

Essa decisão atende às duas casas exigidas na resposta pelo enunciado, que não define o tratamento de frações de centavo.

## Processamento e estados

O dispatcher usa Strategy e seleciona `EntryEventHandler`, `ParkedEventHandler` ou `ExitEventHandler` pelo `event_type`.

Handlers duplicados são detectados na inicialização.

O ciclo permitido é:

```text
ENTERED → PARKED → FINISHED
```

Não são criadas sessões `REJECTED`. Rejeições de negócio são auditadas em `processed_webhook_event` junto com status HTTP e mensagem.

## Respostas HTTP

### Webhook

| Situação | HTTP | `status` |
|---|---:|---|
| Processado | 200 | `PROCESSED` |
| Duplicado compatível | mesmo HTTP e `status` originais | `PROCESSED` ou `REJECTED`, com `duplicate: true` |
| Payload inválido | 400 | `INVALID` |
| Recurso inexistente | 404 | `REJECTED` |
| Conflito de negócio | 409 | `REJECTED` |
| Erro inesperado | 500 | `ERROR` |

Uma resposta de evento também contém `duplicate`, permitindo distinguir uma repetição sem mudar o resultado original. Detalhes internos, SQL e stack traces não são expostos.

### Revenue

| Situação | HTTP |
|---|---:|
| Consulta realizada | 200 |
| Data ou setor inválido | 400 |
| Setor inexistente | 404 |
| Erro inesperado | 500 |

Exemplo de erro:

```json
{
  "status": "INVALID",
  "message": "A data deve estar no formato yyyy-MM-dd, por exemplo: 2026-06-19."
}
```

## Preço dinâmico e cobrança

No evento `PARKED`, o setor é identificado pelas coordenadas da vaga.

O preço dinâmico é calculado com base na ocupação confirmada anterior à nova ocupação e congelado na sessão antes de a vaga ser marcada como ocupada.

| Ocupação anterior | Multiplicador |
|---|---:|
| `0% <= ocupação < 25%` | `0.90` |
| `25% <= ocupação < 50%` | `1.00` |
| `50% <= ocupação < 75%` | `1.10` |
| `75% <= ocupação < 100%` | `1.25` |
| `100%` | setor lotado |

Multiplicadores usam escala 2; preços e valores usam escala 4 e `HALF_UP`.

Até 30 minutos, inclusive, o valor é zero. Depois disso, cobra-se desde a primeira hora, usando o teto da duração total:

- 31 minutos: uma hora;
- 60 minutos: uma hora;
- `60:00.001`: duas horas;
- 70 minutos: duas horas.

A duração da cobrança é sempre calculada entre `ENTRY` e `EXIT`.

## Arquitetura

O projeto utiliza uma arquitetura em camadas inspirada em Clean Architecture e Arquitetura Hexagonal, separando regras de domínio, orquestração da aplicação, integrações de infraestrutura e apresentação HTTP, sem adicionar abstrações desnecessárias para o tamanho da solução.

- `domain`: entidades e regras centrais de setores, vagas, sessões, preço e cobrança;
- `application`: validação, inicialização, orquestração dos eventos e casos de uso;
- `infrastructure`: cliente HTTP, persistência, banco de dados e configurações;
- `presentation`: controllers, DTOs e tratamento global de erros.

As entidades de domínio também são entidades JPA. Não existe uma segunda representação nem mapeadores sem benefício concreto.

## Idempotência

O header opcional `Idempotency-Key` é aplicado apenas ao webhook.

O valor informado é respeitado após `trim` e validação de conteúdo não vazio. Seu hash SHA-256 é protegido por constraint única.

A mesma chave representa uma única requisição lógica:

- mesma chave e mesmo payload canônico: retorna o resultado HTTP e a mensagem originais sem reaplicar efeitos;
- mesma chave e payload diferente: retorna `409 Conflict`;
- mesma chave e `event_type` diferente: retorna `409 Conflict`;
- eventos diferentes devem usar chaves diferentes.

Além do hash da chave, cada evento armazena um fingerprint SHA-256 do request canônico.

Sem o header, são usadas chaves determinísticas:

```text
ENTRY|licensePlate|entryTime
PARKED|licensePlate|sessionEntryTime|latitude|longitude
PARKED|licensePlate|NO_ACTIVE_SESSION|latitude|longitude
EXIT|licensePlate|exitTime
```

A placa é convertida para maiúsculas, instantes usam `Instant.toString()` e coordenadas são normalizadas sem zeros finais.

Evento, resultado e alteração operacional são confirmados na mesma transação. A constraint única protege também contra requisições concorrentes.

O header continua sendo a forma mais robusta de idempotência porque o contrato do simulador não fornece `event_id`; as chaves determinísticas são o fallback da aplicação.

## Concorrência e locks

São usados locks `PESSIMISTIC_WRITE` específicos.

No `PARKED`, a ordem é:

```text
sessão → vaga → setor
```

O lock do setor serializa estacionamentos simultâneos em vagas diferentes e garante que o segundo preço observe a ocupação confirmada pelo primeiro.

No `ENTRY`, os setores são bloqueados por ID para serializar a capacidade global.

A capacidade comprometida é calculada como:

```text
vagas ocupadas + sessões ENTERED
```

Sessões `PARKED` não são contadas duas vezes, pois já correspondem a vagas ocupadas.

As transações de `ENTRY` e `PARKED` usam localmente `READ_COMMITTED`, pois suas contagens precisam observar commits ocorridos enquanto aguardavam os locks. O isolamento global não é alterado.

## Estratégia de startup

Existe uma única propriedade controlando a origem do estado inicial:

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

No reset, o snapshot é buscado e validado antes de qualquer exclusão.

A remoção explícita e a nova carga acontecem na mesma transação, na ordem:

```text
eventos processados → sessões → vagas → setores
```

Falhas provocam rollback integral; não são usadas cascatas destrutivas.

Ao final, um log `INFO` informa origem, setores, vagas ocupadas e livres. Em `DEBUG`, cada setor recebe um resumo com capacidade, ocupação, preço-base e horários.

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

## Flyway e persistência

O Flyway aplica migrations SQL explícitas durante a inicialização.

O Hibernate utiliza `ddl-auto: validate`: ele valida o schema criado pelo Flyway, mas não cria nem altera tabelas automaticamente.

Migrations:

- `V1__create_garage_tables.sql`
- `V2__create_parking_sessions_and_webhook_events.sql`
- `V3__add_webhook_request_fingerprint.sql`

Timestamps absolutos, como `created_at` e `updated_at`, são representados por `Instant` e tratados em UTC.

Horários comerciais, como abertura e fechamento dos setores, são representados por `LocalTime` e armazenados exatamente como horários locais, sem conversão de fuso horário.

## Testes e build

Os testes unitários e do cliente HTTP não dependem do simulador real.

Os testes de integração utilizam MySQL com Testcontainers e exigem que o Docker esteja em execução.

Executar os testes:

```bash
./gradlew clean test
```

Gerar relatório JaCoCo:

```bash
./gradlew clean test jacocoTestReport
```

Executar o build completo:

```bash
./gradlew build
```

Relatórios locais:

```text
build/reports/tests/test/index.html
build/reports/jacoco/test/html/index.html
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
8. consultar `/revenue`;
9. consultar `parking_session` e `processed_webhook_event`.

Ao final:

- a sessão deve estar em `FINISHED`;
- a vaga deve estar livre;
- o faturamento deve refletir o valor persistido no `EXIT`;
- duplicidades não devem gerar novos efeitos de domínio.

## Configuração da aplicação

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

## Premissas e decisões técnicas

1. `open_hour`, `close_hour` e `duration_limit_minutes` são persistidos, mas ainda não influenciam o processamento porque o enunciado não define as regras associadas.
2. A configuração é rejeitada se `max_capacity` não corresponder à quantidade de vagas recebidas para o setor.
3. A validação integral ocorre antes da persistência.
4. Uma configuração inválida interrompe a inicialização.
5. Identificadores e código são escritos em inglês; logs, mensagens de erro e respostas operacionais são escritos em português.
6. O simulador é a fonte da verdade apenas na carga inicial ou no reset explícito.
7. No funcionamento padrão com banco preenchido, o banco preserva integralmente o estado operacional.
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

Uma evolução poderá mover `date` e `sector` para query parameters.

Embora preservado nesta entrega por compatibilidade com o desafio, um body em `GET` não possui semântica geralmente definida no padrão HTTP e pode ter suporte inconsistente entre clientes, proxies e caches.

Exemplo de evolução:

```text
GET /revenue?date=2026-07-18&sector=A
```

### Configuração do preço dinâmico

As faixas de ocupação e seus respectivos multiplicadores poderão ser armazenados no banco, permitindo ajustes de negócio sem alteração de código e sem uma nova implantação da aplicação.

### Estratégia de sincronização

A estratégia atual diferencia carga inicial, retomada do banco e reset explícito.

Evoluções futuras podem incluir versionamento de snapshots, auditoria administrativa do reset ou reconciliação seletiva de configuração sem alterar o estado operacional.

### Regras de horário e permanência

Após alinhamento com a área de negócio, poderão ser implementados:

- bloqueio ou sinalização de estacionamento fora do horário do setor;
- tratamento de veículos acima de `duration_limit_minutes`;
- alertas;
- cobranças adicionais;
- abertura de ocorrências.

### Saída sem estacionamento confirmado

Atualmente, o fluxo exige:

```text
ENTRY → PARKED → EXIT
```

Foi identificado o cenário em que um veículo entra na garagem, não encontra uma vaga disponível ou decide deixar a garagem sem receber um evento `PARKED`.

A capacidade global já é comprometida no momento do `ENTRY`. Entretanto, esse fluxo alternativo não foi implementado porque o enunciado não define:

- se a saída direta deve ser permitida;
- se deve haver gratuidade;
- qual seria o tempo máximo de tolerância;
- se deve existir cobrança após esse período;
- em qual condição a sessão deve ser finalizada e a capacidade global liberada.

Antes de permitir `EXIT` diretamente após `ENTRY`, essa regra deveria ser validada com a área de negócio.

Uma possível evolução seria permitir `ENTRY → EXIT` dentro de uma janela de tolerância, finalizando a sessão, liberando a capacidade global e não gerando cobrança.

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

O comando anterior preserva o volume nomeado do MySQL.

Para apagar também os dados persistidos:

```bash
docker compose down -v
```