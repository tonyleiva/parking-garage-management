# Gerenciamento de estacionamento

Backend responsável por obter do simulador a configuração e a ocupação dos setores e vagas de uma garagem, validar integralmente os dados e reconciliá-los de forma transacional no MySQL.

## Escopo atual

Esta etapa implementa a fundação do projeto e a sincronização inicial da garagem.

Ainda não fazem parte deste escopo:

- eventos `ENTRY`, `PARKED` e `EXIT`;
- webhook de eventos;
- cálculo de preço;
- consulta de receita em `/revenue`.

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

## Arquitetura

O projeto utiliza uma arquitetura em camadas inspirada em Clean Architecture e Arquitetura Hexagonal, separando regras de domínio, orquestração da aplicação, integrações de infraestrutura e apresentação HTTP, sem adicionar abstrações desnecessárias para o tamanho da solução.

- `domain`: entidades e comportamento central de setores e vagas;
- `application`: validação, sincronização e orquestração dos casos de uso;
- `infrastructure`: cliente HTTP, persistência, banco de dados e configurações;
- `presentation`: reservado para a API HTTP das próximas etapas.

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

### 3. Iniciar a aplicação

Com o MySQL e o simulador em execução:

```bash
./gradlew bootRun
```

Durante a inicialização, a aplicação consulta o simulador, valida o snapshot e sincroniza os dados no MySQL.

Para habilitar logs detalhados do projeto:

```bash
LOGGING_LEVEL_COM_TONYLEIVA_PARKINGGARAGE=DEBUG ./gradlew bootRun
```

### 4. Conferir a sincronização

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
FROM flyway_schema_history
ORDER BY installed_rank;
```

Com o snapshot atual do simulador, o resultado esperado é:

- 2 setores;
- 30 vagas;
- horários comerciais iguais aos valores retornados pelo simulador;
- estado de ocupação igual ao snapshot recebido;
- migration `V1__create_garage_tables.sql` registrada no histórico do Flyway.

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
| `GARAGE_SYNCHRONIZATION_ENABLED` | `true` | Habilita a sincronização no startup |

Em testes ou contextos específicos, a sincronização pode ser desabilitada sem alteração de código:

```yaml
garage:
  synchronization:
    enabled: false
```

## Sincronização inicial

No funcionamento normal, `garage.synchronization.enabled` vale `true`.

Durante a inicialização, a aplicação:

1. consulta o endpoint `/garage`;
2. converte o JSON externo para DTOs;
3. valida toda a configuração em memória;
4. cria ou atualiza setores e vagas;
5. sobrescreve o estado de ocupação com o valor recebido;
6. remove vagas e setores ausentes no snapshot atual;
7. conclui toda a reconciliação em uma única transação.

Nesta versão, o simulador é considerado a fonte da verdade da configuração e da ocupação. O banco é reconciliado para refletir exatamente o snapshot atual. Essa decisão foi tomada por ser a interpretação mais aderente ao enunciado.

As restrições únicas de código do setor, identificador externo da vaga e coordenadas impedem duplicações. Reiniciar a aplicação com o mesmo snapshot não cria novos registros.

Caso uma vaga ou setor obsoleto ainda possua referências no banco, a aplicação não força sua exclusão nem utiliza cascata destrutiva. Toda a transação sofre rollback e a inicialização é interrompida com uma mensagem clara.

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

## Datas, horários e logs

Timestamps absolutos, como `created_at` e `updated_at`, são representados por `Instant` e tratados em UTC.

Horários comerciais, como abertura e fechamento dos setores, são representados por `LocalTime` e armazenados exatamente como horários locais, sem conversão de fuso horário.

Em nível `INFO`, a sincronização registra:

- início da operação;
- quantidade de setores e vagas recebidos;
- conclusão da validação;
- resumo final da sincronização.

Em nível `DEBUG`, são registrados os detalhes de cada setor recebido. As vagas não são registradas individualmente, evitando excesso de logs.

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
7. O simulador é a fonte da verdade da ocupação durante a inicialização nesta versão. Em um sistema produtivo, essa responsabilidade deveria ser alinhada com a área de negócio, pois o estado operacional local pode precisar ser preservado após reinicializações.
8. Timestamps absolutos são tratados em UTC, enquanto horários comerciais não sofrem conversão de fuso.

## Evoluções futuras

### Configuração do preço dinâmico

As faixas de ocupação e seus respectivos multiplicadores poderão ser armazenados no banco de dados, permitindo ajustes de negócio sem alteração de código e sem uma nova implantação da aplicação.

### Estratégia de sincronização

Poderá existir uma estratégia configurável para escolher entre:

- usar o simulador como fonte da verdade da configuração e da ocupação;
- preservar o estado operacional local após reinicializações.

### Regras de horário e permanência

Após alinhamento com a área de negócio, poderão ser implementados:

- bloqueio ou sinalização de estacionamento fora do horário do setor;
- tratamento de veículos acima de `duration_limit_minutes`;
- alertas, cobranças adicionais ou abertura de ocorrências.