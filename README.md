# Gerenciamento de estacionamento

[![CI](https://github.com/tonyleiva/parking-garage-management/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/tonyleiva/parking-garage-management/actions/workflows/ci.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=tonyleiva_parking-garage-management&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=tonyleiva_parking-garage-management)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=tonyleiva_parking-garage-management&metric=coverage)](https://sonarcloud.io/summary/new_code?id=tonyleiva_parking-garage-management)
![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)

Backend para gerenciamento de estacionamento, responsável por carregar a configuração da garagem, processar eventos de veículos de forma transacional e consultar o faturamento diário por setor.

A solução prioriza consistência transacional, idempotência, concorrência, testes automatizados e decisões explícitas para as ambiguidades do enunciado.

## Funcionalidades

- carga inicial e reset controlado da garagem;
- processamento dos eventos `ENTRY`, `PARKED` e `EXIT`;
- controle global de capacidade;
- ocupação e liberação de vagas;
- preço dinâmico por ocupação;
- cálculo e persistência da cobrança;
- idempotência com detecção de conflito de payload;
- consulta diária de receita por setor;
- locks pessimistas para proteção contra concorrência;
- migrations com Flyway;
- CI com GitHub Actions, JaCoCo e SonarQube Cloud.

## Stack

- Java 21
- Spring Boot 4.1
- Gradle
- MySQL 8.4
- Spring Data JPA
- Flyway
- Docker Compose
- JUnit 5
- Mockito
- Testcontainers
- JaCoCo
- SonarQube Cloud

## Decisões técnicas

As decisões detalhadas sobre startup, idempotência, concorrência, timezone, persistência financeira, ambiguidades do enunciado e evoluções futuras estão documentadas em:

[Decisões técnicas](docs/technical-decisions.md)

## Qualidade

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

## Executando localmente

### 1. Subir o MySQL

```bash
docker compose up -d mysql
docker compose ps
```

Configuração padrão:

```text
Host: localhost
Porta: 3306
Banco: parking_garage
Usuário: parking_user
Senha: parking_password
```

> No DBeaver, conexões locais com MySQL 8 podem exigir, para ambiente de desenvolvimento, as propriedades `allowPublicKeyRetrieval=true` e `useSSL=false`.

### 2. Iniciar o simulador

Na primeira execução:

```bash
docker run -d \
  --name garage-sim \
  -p 3000:3000 \
  cfontes0estapar/garage-sim:1.0.0
```

No Windows, o mesmo comando pode ser executado em uma única linha:

```powershell
docker run -d --name garage-sim -p 3000:3000 cfontes0estapar/garage-sim:1.0.0
```

Nas execuções seguintes:

```bash
docker start garage-sim
```

Validar:

```bash
curl http://localhost:3000/garage
```

### 3. Iniciar a aplicação

```bash
./gradlew bootRun
```

A aplicação executa na porta `3003`.

Na primeira execução, consulta o simulador, valida o snapshot e realiza a carga inicial. Nas execuções seguintes, por padrão, retoma o estado persistido no MySQL.

Reset explícito:

```bash
GARAGE_STARTUP_RESET_FROM_SIMULATOR=true ./gradlew bootRun
```

Logs detalhados:

```bash
LOGGING_LEVEL_COM_TONYLEIVA_PARKINGGARAGE=DEBUG ./gradlew bootRun
```

## Endpoints

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/webhook` | Recebe eventos `ENTRY`, `PARKED` e `EXIT` |
| `GET` | `/revenue` | Consulta a receita diária de um setor pelo dia do `EXIT` |

Os endpoints acima estão disponíveis em `http://localhost:3003`.

O endpoint `GET /garage` pertence ao simulador externo e fica disponível em `http://localhost:3000/garage`.

## Webhook

### ENTRY

```json
{
  "license_plate": "ZUL0001",
  "entry_time": "2025-01-01T12:00:00.000Z",
  "event_type": "ENTRY"
}
```

Cria uma sessão `ENTERED`. A capacidade global já é comprometida, embora a vaga específica ainda não seja conhecida.

### PARKED

```json
{
  "license_plate": "ZUL0001",
  "lat": -23.561684,
  "lng": -46.655981,
  "event_type": "PARKED"
}
```

Localiza a vaga pelas coordenadas, associa setor e vaga, congela o preço dinâmico e altera a sessão para `PARKED`.

### EXIT

```json
{
  "license_plate": "ZUL0001",
  "exit_time": "2025-01-01T13:10:00.000Z",
  "event_type": "EXIT"
}
```

Libera a vaga, calcula e persiste `amount` e finaliza a sessão.

### Exemplo completo

```bash
curl -i -X POST http://localhost:3003/webhook \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: entry-zul0001-20250101' \
  -d '{"license_plate":"ZUL0001","entry_time":"2025-01-01T12:00:00.000Z","event_type":"ENTRY"}'
```

```bash
curl -i -X POST http://localhost:3003/webhook \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: parked-zul0001-20250101' \
  -d '{"license_plate":"ZUL0001","lat":-23.561684,"lng":-46.655981,"event_type":"PARKED"}'
```

```bash
curl -i -X POST http://localhost:3003/webhook \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: exit-zul0001-20250101' \
  -d '{"license_plate":"ZUL0001","exit_time":"2025-01-01T13:10:00.000Z","event_type":"EXIT"}'
```

## Consulta de receita

Por compatibilidade com o contrato do desafio, `GET /revenue` recebe body JSON:

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

A consulta considera apenas sessões `FINISHED`, usa o dia do `EXIT` em `America/Sao_Paulo`, soma o valor já persistido e retorna `0.00` quando não há faturamento.

## Regras principais

### Fluxo

```text
ENTERED → PARKED → FINISHED
```

### Preço dinâmico

| Ocupação anterior | Multiplicador |
|---|---:|
| `0% <= ocupação < 25%` | `0.90` |
| `25% <= ocupação < 50%` | `1.00` |
| `50% <= ocupação < 75%` | `1.10` |
| `75% <= ocupação < 100%` | `1.25` |
| `100%` | setor lotado |

O preço é calculado e congelado no `PARKED`, quando setor e vaga passam a ser conhecidos.

### Cobrança

- até 30 minutos: gratuito;
- 31 minutos: uma hora;
- 60 minutos: uma hora;
- acima de 60 minutos: arredondamento da duração para cima;
- duração calculada entre `ENTRY` e `EXIT`.

## Arquitetura

A solução utiliza arquitetura em camadas inspirada em Clean Architecture e Arquitetura Hexagonal:

- `domain`: entidades e regras de negócio;
- `application`: casos de uso e orquestração;
- `infrastructure`: persistência, integrações e configurações;
- `presentation`: controllers, DTOs e tratamento de erros.

As entidades de domínio também são entidades JPA, evitando uma segunda representação e mapeadores sem benefício concreto para o tamanho da solução.

## Testes e build

Os testes de integração utilizam MySQL com Testcontainers e exigem Docker ativo.

```bash
./gradlew clean test
./gradlew clean test jacocoTestReport
./gradlew build
```

Relatórios locais:

```text
build/reports/tests/test/index.html
build/reports/jacoco/test/html/index.html
```

## Configuração

| Variável | Valor padrão | Descrição |
|---|---|---|
| `DATABASE_URL` | `jdbc:mysql://localhost:3306/parking_garage` | URL JDBC do MySQL |
| `DATABASE_USERNAME` | `parking_user` | Usuário do banco |
| `DATABASE_PASSWORD` | `parking_password` | Senha do banco |
| `GARAGE_SIMULATOR_BASE_URL` | `http://localhost:3000` | URL base do simulador |
| `GARAGE_SIMULATOR_CONNECT_TIMEOUT` | `2s` | Timeout de conexão |
| `GARAGE_SIMULATOR_READ_TIMEOUT` | `5s` | Timeout de leitura |
| `GARAGE_STARTUP_RESET_FROM_SIMULATOR` | `false` | Recarrega um novo snapshot validado |

## Encerrando o ambiente

```bash
docker stop garage-sim
docker compose stop mysql
```

Para remover containers e rede:

```bash
docker compose down
```

Para apagar também os dados persistidos:

```bash
docker compose down -v
```