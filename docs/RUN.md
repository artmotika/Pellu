# Инструкция по запуску платформы ЦФА

1. Конфигурация окружения (application.yaml)
   Чтобы код работал с вашим локальным компьютером, убедитесь, что в настройках указаны следующие значения:
* solana.rpc.url: http://127.0.0.1:8899 (стандартный порт локального валидатора).
* solana.program.id: Должен в точности совпадать с ID, который вы получили после anchor deploy.
* solana.admin.private-key: Используйте Base58-строку вашего локального ключа (обычно в ~/.config/solana/id.json).

## Требования
*   **Java 21** (JDK 21+)
*   **Docker & Docker Compose**
*   **Gradle 8.x+** (или используйте встроенный `gradlew`)

---

## Шаг 1: Запуск инфраструктуры
Запустите базу данных, брокер сообщений и систему мониторинга:
```bash
docker-compose up -d
```
Это поднимет:
- **PostgreSQL** (порт 54321) — база данных. 
  - **ВАЖНО:** Теперь используются две базы: `auth_db` и `trading_db`. При первом запуске скрипты инициализации создадут их автоматически.
- **Kafka & Zookeeper** (порт 9092) — шина событий.
- **Prometheus** (порт 9090) — сбор метрик.
- **Grafana** (порт 3000) — визуализация (логин: `admin`, пароль: `admin`).

---

## Шаг 2: Конфигурация и запуск
Микросервисы теперь используют паттерн "Database per Service" и взаимодействуют через REST/Kafka.

### Переменные окружения (опционально):
Если вы запускаете сервисы вне Docker, вы можете переопределить настройки:
- `AUTH_SERVICE_URL` — адрес Auth Service (по умолчанию `http://localhost:8083`)
- `TRADING_SERVICE_URL` — адрес Trading Engine (по умолчанию `http://localhost:8081`)
- `DB_URL` — URL подключения к БД (у каждого сервиса своя БД)
- `APP_PLATFORM_WALLET` — адрес кошелька платформы для сделок (в `trading-engine-service`)
- `APP_PLATFORM_TOKEN_ACCOUNT` — адрес ATA платформы для выплаты дивидендов (в `trading-engine-service`)

### Запуск сервисов по порядку:
1. **Auth Service (БД: auth_db, Порт 8083):**
   ```bash
   ./gradlew :auth-service:bootRun
   ```
2. **Trading Engine (БД: trading_db, Порт 8081):**
   ```bash
   ./gradlew :trading-engine-service:bootRun
   ```
3. **Solana Connector (Порт 8082):**
   ```bash
   ./gradlew :solana-connector-service:bootRun
   ```
4. **API Gateway (Порт 8080):**
   ```bash
   ./gradlew :api-gateway-service:bootRun
   ```

---

## Шаг 3: Доступ к интерфейсам
- **API Документация (Swagger):** `http://localhost:8080/swagger-ui.html`
- **Панель Администратора:** Откройте файл `admin-ui/index.html` в любом браузере.
- **Метрики (Prometheus):** `http://localhost:9090`
- **Дашборды (Grafana):** `http://localhost:3000`

---

## Настройка Solana (Devnet)
Сервис настроен на работу с **Solana Devnet**. 
- Убедитесь, что у вас есть интернет-соединение.
- В `solana-connector-service` используется публичный RPC узел `https://api.devnet.solana.com`.
- Для реальных транзакций в `SolanaBlockchainService` необходимо прописать приватный ключ в переменную `adminAccount` (сейчас генерируется новый для демонстрации потока данных).
