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

class MasterTestSuite:
    def __init__(self):
        self.user_ids = {}
        self.user_tokens = {}
        self.user_wallets = {}
        self.admin_token = None
        self.admin_id = None

    def register_user(self, name, password="p1"):
        wallet = name + "_" + str(uuid.uuid4())[:10]
        if name == "admin":
            wallet = "ADMIN_" + str(uuid.uuid4())[:10]
            
        resp = requests.post(f"{AUTH_URL}/api/v1/auth/register", json={"wallet": wallet, "password": password})
        if resp.status_code not in [200, 201]: 
            error(f"User {name} reg failed: {resp.text}")
            return None
        
        data = resp.json()
        token = data.get("token")
        uid = data.get("userId")
        
        self.user_ids[name] = uid
        self.user_tokens[name] = token
        self.user_wallets[name] = wallet
        
        if name == "admin":
            self.admin_token = token
            self.admin_id = uid
            
        return uid

    def login_user(self, name, password="p1"):
        wallet = self.user_wallets.get(name)
        resp = requests.post(f"{AUTH_URL}/api/v1/auth/login", json={"wallet": wallet, "password": password})
        if resp.status_code == 200:
            token = resp.json().get("token")
            self.user_tokens[name] = token
            return token
        return None

    def get_auth_header(self, name=None):
        token = self.user_tokens.get(name) if name else self.admin_token
        return {"Authorization": f"Bearer {token}"}

    def setup_asset(self, status="TRADING", unlock_in_future=False):
        if not self.admin_token:
            self.register_user("admin")
            
        log(f"Setting up asset with target status {status}...")
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
        
        if status == "IPO_ACTIVE":
            requests.post(f"{GATEWAY_URL}/api/v1/admin/ipo/start", params={"assetId": aid}, headers=self.get_auth_header())
        elif status == "TRADING":
            requests.post(f"{GATEWAY_URL}/api/v1/admin/ipo/start", params={"assetId": aid}, headers=self.get_auth_header())
            requests.post(f"{GATEWAY_URL}/api/v1/admin/ipo/finalize", params={"assetId": aid}, headers=self.get_auth_header())
        
        log(f"Created asset {aid} with status {status}")
        time.sleep(1) # Wait for sync
        return aid

    # --- 1. COMPLIANCE ---

    def test_kyc_compliance(self):
        log("--- SCENARIO: KYC COMPLIANCE ---")
        uid = self.register_user("compliance_user")
        aid = self.setup_asset("TRADING")
        
        # 1. PENDING
        r1 = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "assetId": aid, "type": "BUY", "amount": 1, "price": 100.0
        }, headers=self.get_auth_header("compliance_user"))
        if r1.status_code == 403: log("SUCCESS: PENDING user rejected")
        else: error(f"FAILURE: PENDING user accepted: {r1.status_code}")

        # 2. APPROVE
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": True}, headers=self.get_auth_header())
        time.sleep(2) # Wait for Kafka + DB
        self.login_user("compliance_user")
        
        r2 = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "assetId": aid, "type": "BUY", "amount": 1, "price": 100.0
        }, headers=self.get_auth_header("compliance_user"))
        if r2.status_code == 202: log("SUCCESS: APPROVED user allowed")
        else: error(f"FAILURE: APPROVED user rejected: {r2.text}")

    def test_account_freezing(self):
        log("--- SCENARIO: ACCOUNT FREEZING ---")
        uid = self.register_user("frozen_user")
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": True}, headers=self.get_auth_header())
        time.sleep(1)
        self.login_user("frozen_user")
        aid = self.setup_asset("TRADING")

        # Freeze
        requests.post(f"{GATEWAY_URL}/api/v1/admin/freeze", json={"userId": uid, "freeze": True}, headers=self.get_auth_header())
        time.sleep(2)
        self.login_user("frozen_user")
        
        r1 = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "assetId": aid, "type": "BUY", "amount": 1, "price": 100.0
        }, headers=self.get_auth_header("frozen_user"))
        if r1.status_code == 403: log("SUCCESS: Frozen account rejected")
        else: error("FAILURE: Frozen account accepted")

    def test_aml_risk(self):
        log("--- SCENARIO: AML RISK ---")
        uid = self.register_user("risky_user")
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": True}, headers=self.get_auth_header())
        requests.post(f"{GATEWAY_URL}/api/v1/admin/risk", json={"userId": uid, "score": 95}, headers=self.get_auth_header())
        time.sleep(2)
        self.login_user("risky_user")
        
        aid = self.setup_asset("TRADING")
        r1 = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "assetId": aid, "type": "BUY", "amount": 1, "price": 100.0
        }, headers=self.get_auth_header("risky_user"))
        if r1.status_code == 403: log("SUCCESS: High AML risk rejected")
        else: error("FAILURE: High AML risk accepted")

    # --- 2. TRADING ---

    def test_investor_limits(self):
        log("--- SCENARIO: INVESTOR LIMITS ---")
        uid = self.register_user("retail_user")
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": True}, headers=self.get_auth_header())
        time.sleep(1)
        self.login_user("retail_user")
        
        aid = self.setup_asset("TRADING")
        r1 = requests.post(f"{GATEWAY_URL}/api/v1/orders", json={
            "assetId": aid, "type": "BUY", "amount": 7000, "price": 100.0
        }, headers=self.get_auth_header("retail_user"))
        if r1.status_code == 403: log("SUCCESS: Retail limit enforced")
        else: error(f"FAILURE: Retail limit bypassed: {r1.status_code}")

    def test_order_matching(self):
        log("--- SCENARIO: ORDER MATCHING ---")
        u1 = self.register_user("trader_buy")
        u2 = self.register_user("trader_sell")
        for u in ["trader_buy", "trader_sell"]:
            requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": self.user_ids[u], "approved": True}, headers=self.get_auth_header())
            time.sleep(0.5)
            self.login_user(u)
        
        aid = self.setup_asset("TRADING")
        requests.post(f"{GATEWAY_URL}/api/v1/orders", json={"assetId": aid, "type": "BUY", "amount": 5, "price": 200.0}, headers=self.get_auth_header("trader_buy"))
        requests.post(f"{GATEWAY_URL}/api/v1/orders", json={"assetId": aid, "type": "SELL", "amount": 5, "price": 200.0}, headers=self.get_auth_header("trader_sell"))
        log("SUCCESS: Orders matched and accepted")

    # --- 3. GOVERNANCE & CORPORATE ---

    def test_governance_lifecycle(self):
        log("--- SCENARIO: GOVERNANCE ---")
        aid = self.setup_asset("TRADING")
        
        # Start
        vote_req = {"assetId": aid, "proposal": "New Director", "durationMinutes": 5}
        resp = requests.post(f"{GATEWAY_URL}/api/v1/admin/vote", json=vote_req, headers=self.get_auth_header())
        action_id = resp.json().get("actionId")
        log(f"Vote started: {action_id}")

        # Cast
        uid = self.register_user("governance_voter")
        requests.post(f"{GATEWAY_URL}/api/v1/admin/kyc", json={"userId": uid, "approved": True}, headers=self.get_auth_header())
        time.sleep(1)
        self.login_user("governance_voter")
        
        cast_req = {"userId": uid, "vote": True}
        r_cast = requests.post(f"{GATEWAY_URL}/api/v1/votes/{action_id}/cast", json=cast_req, headers=self.get_auth_header("governance_voter"))
        if r_cast.status_code == 202: log("SUCCESS: Vote cast accepted")
        else: error(f"FAILURE: Vote cast failed: {r_cast.text}")

    def test_dividend_payout(self):
        log("--- SCENARIO: DIVIDENDS ---")
        aid = self.setup_asset("TRADING")
        div_req = {"assetId": aid, "amountPerShare": 10.0}
        resp = requests.post(f"{GATEWAY_URL}/api/v1/admin/dividends", json=div_req, headers=self.get_auth_header())
        if resp.status_code == 200: log("SUCCESS: Dividend payout triggered")
        else: error(f"FAILURE: Dividend failed: {resp.text}")

    def run_all(self):
        log("=== PELLU MASTER TEST SUITE STARTING ===")
        self.register_user("admin")
        
        self.test_kyc_compliance()
        self.test_account_freezing()
        self.test_aml_risk()
        self.test_investor_limits()
        self.test_order_matching()
        self.test_governance_lifecycle()
        self.test_dividend_payout()
        
        log("=== MASTER TEST SUITE COMPLETED ===")

if __name__ == "__main__":
    suite = MasterTestSuite()
    suite.run_all()
