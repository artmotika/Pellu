# Инструкция по локальному запуску: Solana (Docker) + Kubernetes (Docker Desktop)

В данном руководстве описано, как запустить блокчейн Solana в Docker, собрать микросервисы и развернуть их в локальном кластере Kubernetes.

---

## Часть 1: Запуск блокчейна Solana в Docker

Использование Docker для Solana избавляет от необходимости устанавливать сложные инструменты (Rust, Solana CLI) напрямую на Windows.

1.  **Запустите контейнер с валидатором:**
    Откройте терминал и выполните команду:
    ```bash
    docker run -d --name solana-validator -p 8899:8899 -p 8900:8900 -p 9900:9900 solanalabs/solana:v1.18.15 solana-test-validator --limit-ledger-size 20000000 --no-bpf-jit --slots-per-epoch 100 --log-history-max-files 5
    ```
    *Эта команда скачает образ и запустит блокчейн в фоновом режиме.*

2.  **Проверка работы валидатора:**
    Проверьте логи контейнера:
    ```bash
    docker logs -f solana-validator
    ```
    Вы должны увидеть лог генерации слотов (напр. `slot: 100, 101...`).

3.  **Развертывание смарт-контракта (через контейнер):**
    Чтобы не устанавливать Anchor локально, можно использовать контейнер для деплоя:
    ```bash
    # Из папки проекта
    docker run --rm -v ${PWD}:/work -w /work/smart-contract projectserum/build:v0.24.2 anchor build
    ```
    *Примечание: Если возникнут сложности с контейнерной сборкой, рекомендуется использовать WSL2.*

4.  **Управление кошельком внутри контейнера:**
    Вы можете выполнять команды `solana` внутри запущенного контейнера:
    ```bash
    # Узнать адрес админа
    docker exec solana-validator solana address
    
    # Переключитесь на локальную сеть
    docker exec solana-validator solana config set --url http://127.0.0.1:8899
    
    # Пополнить баланс
    docker exec solana-validator solana airdrop 10
    
    # Экспортировать приватный ключ (Base58) для настройки Java-сервиса
    docker cp solana-validator:/root/.config/solana/id.json ./private_key/id.json
    ```

---

## Часть 2: Подготовка Kubernetes в Docker Desktop

1.  **Включите Kubernetes:** В настройках Docker Desktop (Settings -> Kubernetes -> Enable Kubernetes).

2.  **Сборка Docker-образов микросервисов:**
    ```bash
    # Выполните в корне проекта
    docker build -t api-gateway:latest -f api-gateway-service/Dockerfile .
    docker build -t auth-service:latest -f auth-service/Dockerfile .
    docker build -t solana-connector:latest -f solana-connector-service/Dockerfile .
    docker build -t trading-engine:latest -f trading-engine-service/Dockerfile .
    ```

---

## Часть 3: Развертывание в Kubernetes

### Настройка сетевого взаимодействия
Поскольку и Solana, и Kubernetes работают в среде Docker Desktop:
*   Валидатор доступен по адресу: `http://host.docker.internal:8899`.

1.  **Конфигурация `solana-connector-deployment.yaml`:**
    Отредактируйте переменные окружения в `k8s-manifests/solana-connector-deployment.yaml`:
    ```yaml
    env:
    - name: SOLANA_RPC_URL
      value: "http://host.docker.internal:8899"
    - name: SOLANA_PROGRAM_ID
      value: "ВАШ_ID_ПОСЛЕ_ДЕПЛОЯ"
    - name: SOLANA_ADMIN_PRIVATE_KEY
      value: "ВАШ_BASE58_КЛЮЧ"
    ```

2.  **Запуск инфраструктуры:**
    ```bash
    kubectl apply -f k8s-manifests/infra/postgres.yaml
    kubectl apply -f k8s-manifests/infra/kafka.yaml
    ```

3.  **Запуск сервисов:**
    ```bash
    kubectl apply -f k8s-manifests/api-gateway-deployment.yaml
    kubectl apply -f k8s-manifests/auth-deployment.yaml
    kubectl apply -f k8s-manifests/trading-engine-deployment.yaml
    kubectl apply -f k8s-manifests/solana-connector-deployment.yaml
    ```

---

## Полезные команды

*   **Перезапуск валидатора:** `docker restart solana-validator`
*   **Очистка данных блокчейна:** `docker rm -f solana-validator` и запуск заново.
*   **Проверка подов:** `kubectl get pods`
*   **Логи коннектора:** `kubectl logs -l app=solana-connector`
