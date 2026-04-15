# Pellu: Advanced DFA Platform on Solana

Pellu is a next-generation platform for issuing and trading Digital Financial Assets (DFA) using the Solana blockchain and a microservices architecture.

![PelluChain Logo](media/logo_anime.gif)

## Key Features

- **Full Asset Lifecycle**: Create assets with legal backing (`legalDocHash`), manage IPO stages (Planned, Active), and enable secondary trading.
- **Corporate Actions on Chain**: 
  - **Voting**: Weight-based voting where vote weight equals token balance.
  - **Dividends**: Automated distribution of payouts to token holders.
- **Regulatory Compliance**:
  - **KYC/AML**: Integrated identity verification; trades are only allowed between KYC-approved wallets.
  - **Account Freezing**: Capability to freeze accounts for AML compliance.
  - **Clawback**: Administrative capability to move assets based on legal requirements.
- **Solana Integration**: Uses Anchor-based smart contracts with Program Derived Addresses (PDA) for registry, voting, and user accounts.

## Tech Stack

- **Backend**: Java 21, Spring Boot 3, Spring Cloud Gateway.
- **Blockchain**: Solana (Rust/Anchor).
- **Messaging**: Apache Kafka for asynchronous service communication.
- **Database**: PostgreSQL with Liquibase.

## Getting Started

1.  **Configure Solana**: Ensure `SOLANA_SETUP.md` is followed for local or devnet deployment.
2.  **Run Infrastructure**: `docker-compose up -d` (Kafka, Postgres).
3.  **Start Services**: Run `gradlew bootRun` in each service directory or use `docker-compose`.

## API Quick Start

### Create Asset
`POST /api/v1/admin/assets`
```json
{
  "name": "Gold Token",
  "totalSupply": 1000000,
  "type": "COMMODITY",
  "ipoPrice": 150.50,
  "legalDocHash": "ipfs://...",
  "tradeUnlockTimestamp": 1713175200
}
```

### Start Voting
`POST /api/v1/admin/vote`
```json
{
  "assetId": "asset-uuid",
  "title": "Stock Split 2024?",
  "options": ["Yes", "No"]
}
```

## API Documentation
Interactive documentation (Swagger UI) is available at:
- Gateway: `http://localhost:8080/swagger-ui.html`
- Engine: `http://localhost:8081/swagger-ui.html`
- Auth: `http://localhost:8083/swagger-ui.html`

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
