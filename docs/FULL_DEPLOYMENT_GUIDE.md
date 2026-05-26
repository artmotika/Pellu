# Полная инструкция по развертыванию платформы Pellu

Эта инструкция содержит все шаги, необходимые для запуска блокчейна Solana, сборки смарт-контракта, компиляции микросервисов и их развертывания в кластере Kubernetes (Docker Desktop).

---

## 1. Подготовка окружения

### Требования
*   **Windows 10/11** с установленным **Docker Desktop** (включен Kubernetes).
*   **WSL 2** (Ubuntu рекомендуется).
*   **Java 21 (JDK)**.
*   **Rust & Solana CLI** (внутри WSL).
*   **Anchor CLI 0.29.0** (внутри WSL).

### Установка инструментов в WSL
Если инструменты еще не установлены, выполните следующие команды в терминале WSL:

1.  **Solana CLI:**
    ```bash
    sh -c "$(curl -sSfL https://release.solana.com/v1.18.15/install)"
    ```
2.  **Anchor (через AVM):**
    ```bash
    cargo install --git https://github.com/coral-xyz/anchor avm --locked --force
    avm install 0.29.0
    avm use 0.29.0
    ```
3.  **Настройка Cargo-бинарников:**
    В новых версиях Solana SDK нужно создать обертку для совместимости с Anchor:
    ```bash
    # Создаем скрипт-адаптер
    BIN_DIR="$HOME/.local/share/solana/install/active_release/bin"
    cat <<EOF > $BIN_DIR/cargo-build-bpf
    #!/bin/bash
    if [ "\$1" == "build-bpf" ]; then shift; fi
    exec cargo-build-sbf "\$@"
    EOF
    chmod +x $BIN_DIR/cargo-build-bpf
    ```

---

## 2. Сборка смарт-контракта

1.  Перейдите в папку контракта в WSL:
    ```bash
    cd /mnt/c/Users/redmi/Git/Pellu/smart-contract
    ```
2.  Очистите старые артефакты (важно для прав доступа):
    ```bash
    sudo rm -rf target
    ```
3.  Соберите контракт:
    ```bash
    anchor build
    ```
    *Результат:* Файл `target/deploy/dfa_advanced_platform.so` должен быть создан.

---

## 3. Сборка Docker-образов

Все команды выполняются из **корневой директории проекта** (можно в PowerShell или WSL).

### Шаг 1: Сборка Java-сервисов
```bash
./gradlew build -x test
```

### Шаг 2: Сборка образа Solana-валидатора
Этот образ будет содержать ваш предустановленный контракт.
```bash
docker build -t solana-validator:local -f Dockerfile.validator .
```

### Шаг 3: Сборка образов микросервисов
```bash
docker build -t auth-service:local-test -f auth-service/Dockerfile .
docker build -t api-gateway:local-test -f api-gateway-service/Dockerfile .
docker build -t solana-connector:local-test -f solana-connector-service/Dockerfile .
docker build -t trading-engine:local-test -f trading-engine-service/Dockerfile .
```

---

## 4. Развертывание в Kubernetes

### Шаг 1: Запуск инфраструктуры
```bash
# Базы данных, брокеры, мониторинг
kubectl apply -f k8s-manifests/infra/postgres.yaml
kubectl apply -f k8s-manifests/infra/redis.yaml
kubectl apply -f k8s-manifests/infra/kafka.yaml
kubectl apply -f k8s-manifests/infra/monitoring.yaml

# Запуск Solana
kubectl apply -f k8s-manifests/infra/solana.yaml
```

### Шаг 2: Пополнение баланса администратора
Для работы `solana-connector` нужно пополнить баланс кошелька в локальной сети Solana.

1.  Найдите имя пода валидатора:
    ```bash
    kubectl get pods -l app=solana-validator
    ```
2.  Выполните Airdrop (замените `POD_NAME` на имя из шага 1):
    ```bash
    # Копируем ключ внутрь для проверки адреса
    kubectl cp private_key/id.json POD_NAME:/tmp/id.json
    # Получаем адрес
    kubectl exec -it POD_NAME -- solana address --keypair /tmp/id.json
    # Делаем airdrop (например, на адрес 4AuX4MF6...)
    kubectl exec -it POD_NAME -- solana airdrop 100 <ВАШ_АДРЕС> --url http://127.0.0.1:8899
    ```

### Шаг 3: Запуск сервисов Pellu
```bash
kubectl apply -f k8s-manifests/auth-deployment.yaml
kubectl apply -f k8s-manifests/api-gateway-deployment.yaml
kubectl apply -f k8s-manifests/solana-connector-deployment.yaml
kubectl apply -f k8s-manifests/trading-engine-deployment.yaml
```

---

## 5. Проверка и Тестирование

### Проверка статуса
Убедитесь, что все поды запущены:
```bash
kubectl get pods
```

### Запуск комплексного теста
Скрипт проверяет все сценарии: KYC, Трейдинг, Замораживание счетов, Голосования и Дивиденды.
```bash
# Убедитесь, что установлены зависимости: pip install requests
python tests/extended_scenarios_test.py
```

---

## Возможные проблемы и решения

*   **Permission Denied в WSL:** Если `anchor build` ругается на права, удалите папку `target` через `sudo rm -rf target`.
*   **Docker Build: failed to read dockerfile:** Если возникает ошибка прав при сборке образа, убедитесь, что файл `.dockerignore` содержит `.pytest_cache`.
*   **CrashLoopBackOff у валидатора:** Проверьте логи `kubectl logs deployment/solana-validator`. Если там ошибка аргументов, убедитесь, что в `solana.yaml` и `Dockerfile.validator` отсутствуют устаревшие флаги вроде `--no-bpf-jit`.
*   **Сервисы не видят Solana:** Проверьте, что в `solana-connector-deployment.yaml` переменная `SOLANA_RPC_URL` установлена в `http://solana-validator:8899`.
