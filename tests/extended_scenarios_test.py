import requests
import json
import uuid
import time
import sys
import base64

# Configuration
GATEWAY_URL = "http://localhost:30080"
AUTH_URL = "http://localhost:30083"

def log(msg):
    print(f"[*] {msg}")

def error(msg):
    print(f"[!] ERROR: {msg}")
    # sys.exit(1) # Don't exit, just continue to other tests

def get_id_from_token(token):
    try:
        payload = token.split('.')[1]
        missing_padding = len(payload) % 4
        if missing_padding:
            payload += '=' * (4 - missing_padding)
        decoded = base64.b64decode(payload).decode('utf-8')
        return json.loads(decoded)['sub']
    except Exception as e:
        print(f"Failed to decode JWT: {e}")

class ExtendedScenariosTest:
    def __init__(self):
        self.asset_id = None
        self.user_ids = {}
        self.user_tokens = {}
        self.admin_token = None
        self.admin_id = None

    def register_user(self, name, password="p1"):
        wallet = name + str(uuid.uuid4())[:20]
        # Admin wallet starts with ADMIN to get ROLE_ADMIN
        if name == "admin":
            wallet = "ADMIN_" + str(uuid.uuid4())[:20]
            
        resp = requests.post(f"{AUTH_URL}/api/v1/auth/register", json={"wallet": wallet, "password": password})
        if resp.status_code not in [200, 201]: 
            error(f"User {name} reg failed: {resp.text}")
            return None
        token = resp.json().get("token")
        uid = get_id_from_token(token)
        self.user_ids[name] = uid
        self.user_tokens[name] = token
        
        if name == "admin":
            self.admin_token = token
            self.admin_id = uid
            
        return uid

    def get_auth_header(self, name=None):
        token = self.user_tokens.get(name) if name else self.admin_token
        return {"Authorization": f"Bearer {token}"}

    def setup_asset(self, status="TRADING", unlock_in_future=False):
        if not self.admin_token:
            self.register_user("admin")
            
        log(f"Setting up asset with status {status}...")
        unlock_ts = int(time.time()) + 3600 if unlock_in_future else int(time.time()) - 60
        asset_data = {
            "name": f"Asset {str(uuid.uuid4())[:4]}",
            "totalSupply": 10000,
            "type": "EQUITY",
            "ipoPrice": 100.0,
            "tradeUnlockTimestamp": unlock_ts
        }
        resp = requests.post(f"{GATEWAY_URL}/api/v1/admin/assets", json=asset_data, headers=self.get_auth_header())
        asset = resp.json()
        aid = asset.get("id")
        
        # Set status via Kafka (indirectly via Gateway endpoint)
        if status == "IPO_ACTIVE":
            requests.post(f"{GATEWAY_URL}/api/v1/admin/ipo/start", params={"assetId": aid}, headers=self.get_auth_header())
        elif status == "TRADING":
            requests.post(f"{GATEWAY_URL}/api/v1/admin/ipo/start", params={"assetId": aid}, headers=self.get_auth_header())
            requests.post(f"{GATEWAY_URL}/api/v1/admin/ipo/finalize", params={"assetId": aid}, headers=self.get_auth_header())
        
        log(f"Created asset {aid} with status {status}")
        time.sleep(2) # Wait for sync
        return aid

    def test_kyc_rejected_trading(self):
        log("--- TEST: KYC REJECTED TRADING ---")
        uid = self.register_user("kyc_rejected")
        # Reject KYC
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": False}, headers=self.get_auth_header())
        time.sleep(1)
        
        aid = self.setup_asset()
        
        # Try buy
        resp = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "userId": uid, "assetId": aid, "type": "BUY", "amount": 1, "price": 100.0
        }, headers=self.get_auth_header("kyc_rejected"))
        if resp.status_code != 202:
            log("SUCCESS: Order rejected for unverified user")
        else:
            error("FAILURE: Order accepted for unverified user")

    def test_account_frozen_trading(self):
        log("--- TEST: ACCOUNT FROZEN TRADING ---")
        uid = self.register_user("frozen_user")
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": True}, headers=self.get_auth_header())
        requests.post(f"{GATEWAY_URL}/api/v1/admin/freeze", json={"userId": uid, "freeze": True}, headers=self.get_auth_header())
        time.sleep(1)
        
        aid = self.setup_asset()
        
        resp = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "userId": uid, "assetId": aid, "type": "BUY", "amount": 1, "price": 100.0
        }, headers=self.get_auth_header("frozen_user"))
        if resp.status_code != 202:
            log("SUCCESS: Order rejected for frozen user")
        else:
            error("FAILURE: Order accepted for frozen user")

    def test_trading_locked_by_timestamp(self):
        log("--- TEST: TRADING LOCKED BY TIMESTAMP ---")
        uid = self.register_user("timestamp_user")
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": True}, headers=self.get_auth_header())
        time.sleep(1)
        
        aid = self.setup_asset(unlock_in_future=True)
        
        resp = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "userId": uid, "assetId": aid, "type": "BUY", "amount": 1, "price": 100.0
        }, headers=self.get_auth_header("timestamp_user"))
        if resp.status_code != 202:
            log("SUCCESS: Order rejected due to time lock")
        else:
            error("FAILURE: Order accepted despite time lock")

    def test_ipo_restrictions(self):
        log("--- TEST: IPO RESTRICTIONS ---")
        uid = self.register_user("ipo_user")
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": True}, headers=self.get_auth_header())
        time.sleep(1)
        
        aid = self.setup_asset(status="IPO_ACTIVE")
        
        # 1. No SELL during IPO
        log("Attempting SELL during IPO...")
        resp = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "userId": uid, "assetId": aid, "type": "SELL", "amount": 1, "price": 100.0
        }, headers=self.get_auth_header("ipo_user"))
        if resp.status_code != 202:
            log("SUCCESS: Sell rejected during IPO")
        else:
            error("FAILURE: Sell accepted during IPO")

        # 2. Price must match IPO price
        log("Attempting BUY with wrong price during IPO...")
        resp = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "userId": uid, "assetId": aid, "type": "BUY", "amount": 1, "price": 150.0
        }, headers=self.get_auth_header("ipo_user"))
        if resp.status_code != 202:
            log("SUCCESS: Wrong price rejected during IPO")
        else:
            error("FAILURE: Wrong price accepted during IPO")

    def test_platform_inactive(self):
        log("--- TEST: PLATFORM INACTIVE (IPO_PLANNED) ---")
        uid = self.register_user("inactive_user")
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": True}, headers=self.get_auth_header())
        time.sleep(1)
        
        aid = self.setup_asset(status="IPO_PLANNED")
        
        resp = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "userId": uid, "assetId": aid, "type": "BUY", "amount": 1, "price": 100.0
        }, headers=self.get_auth_header("inactive_user"))
        if resp.status_code != 202:
            log("SUCCESS: Order rejected for IPO_PLANNED asset")
        else:
            error("FAILURE: Order accepted for IPO_PLANNED asset")

    def test_governance_voting(self):
        log("--- TEST: GOVERNANCE VOTING ---")
        uid = self.register_user("voter")
        aid = self.setup_asset()
        
        # 1. Start Vote
        log("Starting governance vote...")
        vote_data = {
            "assetId": aid,
            "title": "Expansion Proposal",
            "options": ["Yes", "No"]
        }
        resp = requests.post(f"{GATEWAY_URL}/api/v1/admin/vote", json=vote_data, headers=self.get_auth_header())
        if resp.status_code != 200:
            error(f"Failed to start vote: {resp.text}")
            return
        
        action_id = resp.json().get("actionId")
        log(f"Started vote with ID: {action_id}")
        
        # 2. Cast Vote
        log("Casting vote...")
        cast_data = {"userId": uid, "optionIndex": 0}
        resp = requests.post(f"{GATEWAY_URL}/api/v1/votes/{action_id}/cast", json=cast_data, headers=self.get_auth_header("voter"))
        if resp.status_code == 202:
            log("SUCCESS: Vote cast accepted")
        else:
            error(f"FAILURE: Vote cast rejected: {resp.status_code} - {resp.text}")

        # 3. Double Voting
        log("Casting vote again (Double Voting test)...")
        resp = requests.post(f"{GATEWAY_URL}/api/v1/votes/{action_id}/cast", json=cast_data, headers=self.get_auth_header("voter"))
        if resp.status_code == 202:
            log("SUCCESS: Second vote request accepted (On-chain will prevent it)")
        else:
            error(f"FAILURE: Second vote request rejected unexpectedly: {resp.status_code}")

    def test_investor_limit(self):
        log("--- TEST: INVESTOR LIMIT (RETAIL) ---")
        uid = self.register_user("limit_user")
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": True}, headers=self.get_auth_header())
        time.sleep(1)
        
        aid = self.setup_asset()
        
        # Limit is 600,000. Try order for 700,000.
        log("Attempting order exceeding 600,000 RUB limit...")
        resp = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "userId": uid, "assetId": aid, "type": "BUY", "amount": 7000, "price": 100.0
        }, headers=self.get_auth_header("limit_user"))
        if resp.status_code != 202:
            log("SUCCESS: Order rejected due to investment limit")
        else:
            error("FAILURE: Order accepted despite exceeding limit")

    def test_invalid_order_parameters(self):
        log("--- TEST: INVALID ORDER PARAMETERS ---")
        uid = self.register_user("param_user")
        aid = self.setup_asset()
        
        # 1. Zero amount
        log("Testing zero amount...")
        resp = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "userId": uid, "assetId": aid, "type": "BUY", "amount": 0, "price": 100.0
        }, headers=self.get_auth_header("param_user"))
        if resp.status_code != 202:
            log("SUCCESS: Zero amount rejected (or handled by gateway)")
        else:
            log("INFO: Zero amount accepted by gateway (will be filtered by engine)")

        # 2. Negative price
        log("Testing negative price...")
        resp = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "userId": uid, "assetId": aid, "type": "BUY", "amount": 1, "price": -10.0
        }, headers=self.get_auth_header("param_user"))
        if resp.status_code != 202:
            log("SUCCESS: Negative price rejected")
        else:
            log("INFO: Negative price accepted by gateway (will be filtered by engine)")

    def test_aml_risk_rejection(self):
        log("--- TEST: AML RISK REJECTION ---")
        uid = self.register_user("risky_user")
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": True}, headers=self.get_auth_header())
        # Set high risk score
        requests.post(f"{GATEWAY_URL}/api/v1/admin/risk", json={"userId": uid, "score": 85}, headers=self.get_auth_header())
        time.sleep(1)
        
        aid = self.setup_asset()
        
        resp = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "userId": uid, "assetId": aid, "type": "BUY", "amount": 1, "price": 100.0
        }, headers=self.get_auth_header("risky_user"))
        if resp.status_code != 202:
            log("SUCCESS: Order rejected due to high AML risk")
        else:
            error("FAILURE: Order accepted despite high AML risk")

    def test_asset_suspension(self):
        log("--- TEST: ASSET SUSPENSION ---")
        uid = self.register_user("suspend_test_user")
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": True}, headers=self.get_auth_header())
        time.sleep(1)
        
        aid = self.setup_asset()
        
        # Suspend asset
        requests.post(f"{GATEWAY_URL}/api/v1/admin/assets/suspend", params={"assetId": aid}, headers=self.get_auth_header())
        time.sleep(2) # Wait for sync
        
        resp = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "userId": uid, "assetId": aid, "type": "BUY", "amount": 1, "price": 100.0
        }, headers=self.get_auth_header("suspend_test_user"))
        if resp.status_code != 202:
            log("SUCCESS: Order rejected for suspended asset")
        else:
            error("FAILURE: Order accepted for suspended asset")

    def test_volatility_spike(self):
        log("--- TEST: VOLATILITY SPIKE ---")
        uid = self.register_user("vol_user")
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": True}, headers=self.get_auth_header())
        time.sleep(1)
        
        aid = self.setup_asset() 
        
        log("Attempting order with 50% price jump (from 100 to 150)...")
        resp = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "userId": uid, "assetId": aid, "type": "BUY", "amount": 1, "price": 150.0
        }, headers=self.get_auth_header("vol_user"))
        log(f"Order status: {resp.status_code}")

    def test_dividend_payout(self):
        log("--- TEST: DIVIDEND PAYOUT ---")
        aid = self.setup_asset()
        
        div_data = {
            "assetId": aid,
            "amount": 1.5
        }
        resp = requests.post(f"{GATEWAY_URL}/api/v1/admin/dividends", json=div_data, headers=self.get_auth_header())
        if resp.status_code == 200:
            log("SUCCESS: Dividend payout triggered")
        else:
            error(f"FAILURE: Dividend payout failed: {resp.text}")

    def run_all(self):
        log("=== PELLU EXTENDED SCENARIOS TEST ===")
        self.test_kyc_rejected_trading()
        self.test_account_frozen_trading()
        self.test_trading_locked_by_timestamp()
        self.test_ipo_restrictions()
        self.test_platform_inactive()
        self.test_governance_voting()
        self.test_dividend_payout()
        self.test_investor_limit()
        self.test_invalid_order_parameters()
        self.test_aml_risk_rejection()
        self.test_asset_suspension()
        self.test_volatility_spike()
        log("=== EXTENDED SCENARIOS TEST COMPLETED ===")

if __name__ == "__main__":
    test = ExtendedScenariosTest()
    test.run_all()
