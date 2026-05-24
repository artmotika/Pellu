# Pellu: High-Performance DFA Trading Platform <img src="media/solana-sol-logo.png" width="40" height="40" align="top" alt="Solana Logo">

**Pellu** is a next-generation, institutional-grade platform for issuing and trading Digital Financial Assets (DFA). It combines the **extreme speed** of an in-memory matching engine with the **absolute security** of on-chain settlement on the Solana blockchain.

![Pellu Platform](media/logo_anime.gif)

---

## 🚀 Why Pellu?

Pellu is designed for high-concurrency environments where performance and regulatory compliance are non-negotiable.

- **Extreme Scalability**: Powered by **Java 21 Virtual Threads (Project Loom)**, handling thousands of concurrent connections with minimal overhead.
- **Stateless Architecture**: Zero-DB Gateway validation using enriched JWT claims and a **Redis-backed Global State Cache**.
- **Hybrid DEX Model**: Off-chain matching for sub-millisecond response times + On-chain settlement (Anchor/Solana) for immutable transparency.
- **Built-in Compliance**: Automated KYC/AML enforcement, investor limits (Retail/Qualified), and account freezing logic.

---

## 🛠️ Tech Stack

- **Core**: Java 21, Spring Boot 3.2, Spring Cloud Gateway
- **Distributed State**: Redis 7 (Hash-based state storage)
- **Messaging**: Apache Kafka (Event-driven inter-service communication)
- **Blockchain**: Rust, Anchor Framework (Solana Program)
- **Database**: PostgreSQL 15, Liquibase
- **DevOps**: Docker, Kubernetes (K8s Manifests included)
- **Testing**: Locust (Load Testing), Python (Functional Master Suite)

---

## 🏗️ Microservices Overview

1.  **API Gateway**: The entry point. Performs **stateless JWT validation** and serves high-speed market data from the Redis cache.
2.  **Auth Service**: Manages user lifecycles, KYC status, and identity. Optimized with BCrypt and distributed token management.
3.  **Trading Engine**: The heart of the platform. Features an **in-memory matching engine**, processes IPOs, and maintains the order book.
4.  **Solana Connector**: The bridge between Web2 and Web3. Listens to Kafka events to trigger on-chain transactions and settlement.

---

## 💎 Key Features

### ⚖️ Regulatory Compliance
*   **KYC/AML Flow**: Integrated moderations. Only `APPROVED` users can trade.
*   **Account Controls**: Instantly freeze suspicious accounts or perform administrative clawbacks.
*   **Investor Limits**: Hard limits on annual investments for retail investors (600,000 RUB) to meet local regulations.

### 📈 Trading & Market
*   **Asset Lifecycle**: Managed IPOs (Planned -> Active -> Finalized) and secondary market trading.
*   **Order Matching**: High-speed matching of BUY/SELL orders by price and asset.
*   **Real-time State**: Redis mirrors the database state for ultra-low latency GET requests.

### 🗳️ Corporate Actions
*   **On-chain Voting**: Token-weighted governance. Vote weight corresponds to the holder's balance.
*   **Dividends**: Trigger automated dividend payouts to all token holders via a single administrative action.

---

## 📊 Performance Benchmarks

Pellu has been rigorously verified under extreme load:
*   **Throughput**: ~500+ requests per second.
*   **Latency**: Order placement P99 at 80ms.
*   **Stability**: **100% Success Rate** (0 failures) during a 10,000-user concurrent stress test.

---

## 🚦 Getting Started

### Prerequisites
- Java 21+
- Docker & Docker Compose
- Solana CLI & Anchor (for contract development)

### Quick Deployment
1.  **Infrastructure**:
    ```bash
    docker-compose up -d  # Starts Kafka, Redis, Postgres
    ```
2.  **Build & Deploy (K8s)**:
    ```bash
    ./gradlew clean build -x test
    # Build docker images and apply manifests from /k8s-manifests
    ```

### Running Tests
*   **Functional Master Suite**:
    ```bash
    python tests/extended_scenarios_test.py
    ```
*   **Load Test (Locust)**:
    ```bash
    locust -f tests/load_test_locust.py --headless -u 100 -r 10 --run-time 1m
    ```

---

## 📜 License
Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
