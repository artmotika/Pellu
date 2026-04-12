# DFA Trading Platform - Master's Thesis Project

## Architecture Overview

```mermaid
sequenceDiagram
    participant User
    participant ESIA as ESIA (Mock)
    participant Auth as Auth Service
    participant Gateway as API Gateway
    participant Engine as Trading Engine
    participant Tax as Tax Module
    participant Solana as Solana Connector
    participant Blockchain as Solana Devnet

    User->>ESIA: Login via Gosuslugi
    ESIA-->>Auth: Verified Identity
    Auth-->>User: JWT Token (isQualified=false)
    
    User->>Gateway: Submit Buy Order
    Gateway->>Gateway: Check 600k Limit (Retail)
    Gateway->>Gateway: AML Frequency Check
    Gateway->>Engine: Kafka: orders.created
    
    Engine->>Engine: Circuit Breaker (Caffeine)
    Engine->>Engine: Save Order (PENDING)
    Engine->>Solana: Kafka: orders.validated
    
    Solana->>Blockchain: Anchor Program Call
    Blockchain-->>Solana: Transaction Finalized
    Solana->>Engine: Kafka: trades.executed
    
    Engine->>Engine: Update Status (COMPLETED)
    Engine->>Engine: Update Price Cache
    Engine->>Tax: Calculate 13% NDFL
    Engine->>Engine: Save Trade Ledger
```

## Key Enterprise Features

1.  **State Identity Integration:** Mocked ESIA (Gosuslugi) flow for instant KYC.
2.  **Regulatory Limits:** Enforced 600,000 RUB annual limit for non-qualified investors.
3.  **Tax Automation:** Automatic calculation of 13% NDFL on successful exits.
4.  **High-Performance Engine:** Caffeine-based in-memory price tracking and sliding window volatility checks.
5.  **Corporate Actions:** Automated dividend payout engine integrated with Solana token transfers.
6.  **Full Observability:** Prometheus/Grafana monitoring with custom business metrics.
7.  **Admin Dashboard:** Centralized control for KYC, AML, and Corporate Actions.

## API Documentation
Interactive documentation (Swagger UI) is available at:
- Gateway: `http://localhost:8080/swagger-ui.html`
- Engine: `http://localhost:8081/swagger-ui.html`
- Auth: `http://localhost:8083/swagger-ui.html`
