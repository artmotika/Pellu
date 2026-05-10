import random
import uuid
import time
from locust import task, between, events, FastHttpUser, tag

# Configuration
# Use NodePort IPs for reliability during high load
AUTH_SERVICE_URL = "http://127.0.0.1:30083"
GATEWAY_URL = "http://127.0.0.1:30080"

class TradingUser(FastHttpUser):
    """
    Highly robust TradingUser for extreme load.
    1. Each user creates their own private 'mod' to approve themselves.
    2. Guaranteed sequential setup: Register -> Admin Reg -> Approve -> Login -> Verify.
    """
    host = GATEWAY_URL
    wait_time = between(0.1, 0.5) 
    connection_timeout = 30.0
    network_timeout = 30.0

    def on_start(self):
        """
        Setup phase with individual admin to avoid race conditions.
        """
        self.user_id = str(uuid.uuid4())
        self.wallet = f"lt_{self.user_id[:8]}"
        self.password = "p1"
        self.auth_token = None
        self.asset_id = self.environment.parsed_options.asset_id

        # 1. Register User
        with self.client.post(
            f"{AUTH_SERVICE_URL}/api/v1/auth/register",
            json={"wallet": self.wallet, "password": self.password},
            name="Auth: User Reg",
            catch_response=True
        ) as resp:
            if resp.status_code not in [200, 201]:
                resp.failure(f"User Reg failed: {resp.status_code} - {resp.text}")
                return
            self.user_id = resp.json().get("userId")

        # 2. Create private Admin for this session
        admin_wallet = f"ADMIN_{self.user_id[:8]}"
        admin_token = None
        with self.client.post(
            f"{AUTH_SERVICE_URL}/api/v1/auth/register",
            json={"wallet": admin_wallet, "password": "admin"},
            name="Auth: Admin Reg",
            catch_response=True
        ) as r:
            if r.status_code in [200, 201]:
                admin_token = r.json().get("token")
            else:
                resp.failure(f"Admin Reg failed: {r.status_code}")
                return

        # 3. Approve KYC
        with self.client.post(
            f"{GATEWAY_URL}/api/v1/admin/kyc",
            json={"userId": self.user_id, "approved": True},
            headers={"Authorization": f"Bearer {admin_token}"},
            name="Admin: KYC Approve",
            catch_response=True
        ) as k_resp:
            if k_resp.status_code != 200:
                k_resp.failure(f"KYC Approve failed: {k_resp.status_code}")
                return

        # 4. Wait & Verify Loop
        max_retries = 20
        for i in range(max_retries):
            time.sleep(1.0)
            with self.client.post(
                f"{AUTH_SERVICE_URL}/api/v1/auth/login",
                json={"wallet": self.wallet, "password": self.password},
                name="Auth: Verify Login",
                catch_response=True
            ) as f_resp:
                if f_resp.status_code == 200:
                    token = f_resp.json().get("token")
                    # Check if шлюз accepts this token AND it has APPROVED claim
                    with self.client.get("/api/v1/trading/me",
                                        headers={"Authorization": f"Bearer {token}"},
                                        name="Auth: Verify Claims",
                                        catch_response=True) as check:
                        if check.status_code == 200:
                            user_data = check.json()
                            if user_data.get("kycStatus") == "APPROVED":
                                self.auth_token = token
                                return # SUCCESS
                            elif i == max_retries - 1:
                                check.failure(f"KYC status is {user_data.get('kycStatus')} for {self.user_id}")
                        elif i == max_retries - 1:
                            check.failure(f"Claims check failed with {check.status_code} for {self.user_id}")
                elif i == max_retries - 1:
                    f_resp.failure(f"Verify login failed for {self.user_id}")

    @tag('trading')
    @task(20) 
    def trade(self):
        if not self.auth_token: return

        # Matchable price set
        prices = [98.0, 99.0, 100.0, 101.0, 102.0]
        order_data = {
            "assetId": self.asset_id,
            "type": random.choice(["BUY", "SELL"]),
            "amount": random.randint(1, 5),
            "price": random.choice(prices)
        }

        with self.client.post(
            "/api/v1/orders",
            json=order_data,
            headers={"Authorization": f"Bearer {self.auth_token}"},
            name="Trade: Place Order",
            catch_response=True
        ) as response:
            if response.status_code == 202:
                response.success()
            else:
                response.failure(f"Order rejected: {response.status_code} - {response.text}")

    @tag('browsing')
    @task(1) 
    def get_info(self):
        if not self.auth_token: return
        self.client.get(f"/api/v1/trading/assets/{self.asset_id}", 
                        headers={"Authorization": f"Bearer {self.auth_token}"},
                        name="Query: Get Asset")

@events.init_command_line_parser.add_listener
def _(parser):
    parser.add_argument("--asset-id", type=str, env_var="ASSET_ID", default="13d6d819-6591-4af2-91da-fab7b43342c2", help="Active asset ID")

@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    print(f"[*] STARTING TRADE LOAD TEST on {GATEWAY_URL}")
    print(f"[*] ASSET: {environment.parsed_options.asset_id}")

@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    print("[*] LOAD TEST COMPLETED.")
