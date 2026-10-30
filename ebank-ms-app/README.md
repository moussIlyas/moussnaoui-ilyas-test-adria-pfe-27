# Ebank — micro-services bancaires

Application bancaire de démonstration construite avec **Spring Boot 4 / Spring Cloud / Spring AI**,
exposant les mêmes opérations métier en **REST**, **GraphQL** et **MCP**, avec un **chatbot agentique**,
une communication asynchrone **Kafka** et un client **React**.

## Architecture

| Module | Rôle | Port |
|---|---|---|
| `config-service` (racine du repo) | Spring Cloud Config Server, backend natif sur `config-repo/` | `8888` |
| `discovery-service` | Eureka | `8761` |
| `gateway-service` | Spring Cloud Gateway, point d'entrée unique | `9999` |
| `account-service` | Comptes, soldes, débit/crédit, consumer Kafka | `8057` |
| `transaction-service` | Transactions, Feign + Resilience4j, producer Kafka | `8056` |
| `ebank-bot` | Agent AI + client MCP (account / transaction) | `8058` |
| `ebank-web` | Client React (Vite) | `5173` |

Chaque service métier suit les mêmes couches :

```
domain/       entités et enums
repository/   accès JPA
service/      règles métier (source unique de vérité)
web/rest/     exposition REST
web/graphql/  exposition GraphQL
mcp/          tools MCP
messaging/    Kafka (producer ou consumer)
```

REST, GraphQL et MCP **délèguent tous à la même couche `service/`**.

## Prérequis

- Java 21
- Node 20+ (client React)
- Docker (MariaDB, Kafka, Keycloak)

## Démarrage

```bash
cp .env.example .env        # secrets locaux (Ollama, Tavily, DB)

# 1. Infrastructure
docker compose -f docker-compose.infra.yml up -d

# 2. Services (dans l'ordre, un terminal par service)
./mvnw -B -DskipTests package                                      # config-service
cd discovery-service        && ../mvnw -B -DskipTests package
cd gateway-service          && ../mvnw -B -DskipTests package
cd account-service          && ../mvnw -B -DskipTests package
cd transaction-service      && ../mvnw -B -DskipTests package
cd ebank-bot                && ../mvnw -B -DskipTests package

java -jar target/<module>-0.0.1-SNAPSHOT.jar
```

Ou tout le stack applicatif en Docker :

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build --wait
```

Le stack Docker contient aussi `config-service` ; MariaDB, Kafka et Keycloak restent dans
`docker-compose.infra.yml` pour le développement depuis l'IDE.

## Configuration partagée

`config-repo/application.properties` est servi par le config-service à tous les services :

```properties
bank.transaction.max-amount=1000
app.kafka.transactions-topic=bank.transactions.created
```

`transaction-service` refuse toute transaction dépassant ce plafond. La valeur est visible sur :

```bash
curl http://localhost:8888/transaction-service/dev
curl http://localhost:8056/actuator/env/bank.transaction.max-amount
```

## API REST

Comptes (`account-service`, via la gateway) :

```bash
curl http://localhost:9999/api/accounts
curl http://localhost:9999/api/accounts/11111111-1111-1111-1111-111111111111
curl -X POST http://localhost:9999/api/accounts/11111111-1111-1111-1111-111111111111/debit \
  -H 'Content-Type: application/json' -d '{"amount":50,"currency":"EUR"}'
```

Transactions et virements (`transaction-service`, via la gateway) :

```bash
curl http://localhost:9999/api/transactions

curl -X POST http://localhost:9999/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"11111111-1111-1111-1111-111111111111","amount":250,"currency":"EUR","merchantCategory":"FOOD","country":"FR"}'

curl -X POST http://localhost:9999/api/transfers \
  -H 'Content-Type: application/json' \
  -d '{"fromAccountId":"11111111-1111-1111-1111-111111111111","toAccountId":"22222222-2222-2222-2222-222222222222","amount":100,"currency":"EUR"}'
```

## GraphQL

```bash
curl -X POST http://localhost:9999/api/accounts-graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ accounts { id customerReference status currency balance } }"}'

curl -X POST http://localhost:9999/api/transactions-graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ transactions { id accountId amount type createdAt } }"}'
```

## MCP

Chaque service métier expose un serveur MCP streamable HTTP sur `/mcp` :

- `account-service` : `getAllAccounts`, `getAccountById`, `createAccount`, `debitAccount`, `creditAccount`
- `transaction-service` : `createTransaction`, `transfer`, `listTransactions`, `getTransaction`, `getAccountBalance`

`ebank-bot` découvre automatiquement ces tools via `spring.ai.mcp.client.streamable-http.connections.*`.

## Kafka (partie F)

Après persistance d'une transaction, `transaction-service` publie dans
`bank.transactions.created` avec **`accountId` comme clé** :

```json
{
  "eventId": "UUID",
  "eventType": "TransactionCreated",
  "transactionId": "UUID",
  "accountId": "UUID",
  "amount": 250.00,
  "currency": "EUR",
  "occurredAt": "2026-01-15T10:30:00Z"
}
```

`account-service` consomme ce topic dans le groupe `account-service` et **journalise uniquement**
l'événement, sans modifier les données bancaires (`TransactionEventConsumer`).

## Chatbot agentique

```bash
# Client REST simple
curl 'http://localhost:9999/api/chat?query=Liste%20mes%20comptes'

# Client JSON
curl -X POST http://localhost:9999/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"demo","message":"Quel est le solde du compte CUST-001 ?"}'
```

L'agent (Ollama cloud + Tavily) utilise les tools MCP pour lire les comptes, créer des paiements et
effectuer des virements. Sans clés API, les endpoints REST/GraphQL/MCP restent utilisables.

## Client React

```bash
cd ebank-web
npm install
npm run dev
```

Ouvrir http://localhost:5173. Vite proxifie `/api` vers la gateway (`http://localhost:9999`).

Fonctionnalités : synthèse des soldes, liste des comptes, historique des transactions, formulaire
paiement / virement, et assistant conversationnel connecté au chatbot.

## Tolérance aux pannes

`transaction-service` appelle `account-service` via OpenFeign. Chaque appel est protégé par un
`@CircuitBreaker` Resilience4j (`accountService`). Si `account-service` est indisponible, l'appel
retourne proprement un **HTTP 503** au lieu d'une erreur Feign brute ; les erreurs métier 400/404
sont traduites en `400 Bad Request`.

Paramètres dans `config-repo/transaction-service-dev.properties` :

```properties
resilience4j.circuitbreaker.instances.accountService.sliding-window-size=10
resilience4j.circuitbreaker.instances.accountService.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.accountService.wait-duration-in-open-state=10s
```

## Données de démonstration

| Compte | Référence | Statut | Solde initial |
|---|---|---|---|
| `11111111-1111-1111-1111-111111111111` | CUST-001 | ACTIVE | 5 000,00 EUR |
| `22222222-2222-2222-2222-222222222222` | CUST-002 | ACTIVE | 2 500,00 EUR |
| `33333333-3333-3333-3333-333333333333` | CUST-003 | BLOCKED | 800,00 EUR |

Les comptes sont créés au premier démarrage de `account-service` (base vide).
