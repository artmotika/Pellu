import requests
import json
import uuid
import time
import sys
import base64

# In Kubernetes, we expose services via NodePorts for testing from outside
GATEWAY_URL = "http://localhost:30080"
AUTH_URL = "http://localhost:30083"

def log(msg):
    print(f"[*] {msg}")

def error(msg):
    print(f"[!] ERROR: {msg}")
    sys.exit(1)

def get_id_from_token(token):
    payload = token.split('.')[1]
    # Add padding if needed
    missing_padding = len(payload) % 4
    if missing_padding:
        payload += '=' * (4 - missing_padding)
    decoded = base64.b64decode(payload).decode('utf-8')
    return json.loads(decoded)['sub']

def test_onboarding():
    log("Starting Phase 1: User Onboarding")
    
    user_wallet = "5HzW8L" + str(uuid.uuid4())[:26]
    
    log(f"Registering User ({user_wallet})...")
    resp = requests.post(f"{AUTH_URL}/api/v1/auth/register", json={
        "wallet": user_wallet,
        "password": "password123"
    })
    if resp.status_code not in [200, 201]:
        error(f"Registration failed: {resp.text}")
    
    token = resp.json().get("token")
    user_id = get_id_from_token(token)
    log(f"User ID: {user_id}")

    log("Approving KYC (via Gateway -> Kafka -> Auth)...")
    resp_kyc = requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={
        "userId": user_id,
        "approved": True
    })
    if resp_kyc.status_code != 200:
        error(f"KYC approval command failed: {resp_kyc.text}")
    
    log("Waiting for KYC status propagation...")
    time.sleep(5)
    
    return user_id

def test_asset_lifecycle():
    log("Starting Phase 2: Asset Lifecycle")
    
    log("Creating new asset (IPO_PLANNED)...")
    asset_data = {
        "name": f"Pellu Token {str(uuid.uuid4())[:4]}",
        "totalSupply": 1000000,
        "type": "EQUITY",
        "ipoPrice": 50.00,
        "legalDocHash": "QmXoypizjW3WknFiJnKLwHCnL72vedxjQkDDP1mXWo6uco",
        "tradeUnlockTimestamp": int(time.time()) + 3600
    }
    resp = requests.post(f"{GATEWAY_URL}/api/v1/admin/assets", json=asset_data)
    if resp.status_code not in [200, 201]:
        error(f"Asset creation failed: {resp.text}")
    
    asset_id = resp.json().get("id")
    log(f"Asset created: {asset_id}")

    log(f"Activating IPO for asset {asset_id}...")
    resp_start = requests.post(f"{GATEWAY_URL}/api/v1/admin/ipo/start", params={"assetId": asset_id})
    if resp_start.status_code != 200:
        error(f"IPO start failed: {resp_start.text}")
    
    log("Finalizing IPO (Moving to TRADING status)...")
    resp_fin = requests.post(f"{GATEWAY_URL}/api/v1/admin/ipo/finalize", params={"assetId": asset_id})
    if resp_fin.status_code != 200:
        error(f"IPO finalize failed: {resp_fin.text}")

    log("Waiting for Asset status propagation...")
    time.sleep(5)

    return asset_id

def test_trading(user_id, asset_id):
    log("Starting Phase 3: Secondary Trading")
    
    log(f"Submitting SELL order for user {user_id}...")
    order_data = {
        "userId": user_id,
        "assetId": asset_id,
        "type": "SELL",
        "amount": 100,
        "price": 55.00
    }
    resp = requests.post(f"{GATEWAY_URL}/api/v1/orders", json=order_data)
    if resp.status_code in [200, 201, 202]:
        log(f"SUCCESS: Order Accepted! {resp.text}")
    else:
        error(f"Order failed: {resp.status_code} - {resp.text}")

if __name__ == "__main__":
    log("=== Pellu Platform K8s Integration Test (Final) ===")
    try:
        u_id = test_onboarding()
        a_id = test_asset_lifecycle()
        test_trading(u_id, a_id)
        log("=== ALL PHASES COMPLETED SUCCESSFULLY ===")
    except Exception as e:
        error(f"Test suite failed: {str(e)}")
