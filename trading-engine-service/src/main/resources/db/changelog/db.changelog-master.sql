CREATE TABLE users (
   id VARCHAR(255) PRIMARY KEY,
   wallet_address VARCHAR(255) NOT NULL UNIQUE,
   kyc_status VARCHAR(50) NOT NULL,
   aml_risk_score INT DEFAULT 0,
   password VARCHAR(255),
   is_qualified BOOLEAN DEFAULT FALSE
);

CREATE TABLE investor_limits (
    user_id VARCHAR(255) PRIMARY KEY REFERENCES users(id),
    annual_investment DECIMAL(19, 4) DEFAULT 0,
    last_reset TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tax_ledger (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) REFERENCES users(id),
    order_id VARCHAR(255) REFERENCES orders(id),
    tax_amount DECIMAL(19, 4) NOT NULL,
    timestamp TIMESTAMP NOT NULL
);

CREATE TABLE corporate_actions (
    id VARCHAR(255) PRIMARY KEY,
    asset_id VARCHAR(255) REFERENCES assets(id),
    type VARCHAR(50) NOT NULL, -- DIVIDEND, COUPON
    amount_per_share DECIMAL(19, 4) NOT NULL,
    status VARCHAR(50) NOT NULL, -- PENDING, COMPLETED
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE assets (
    id VARCHAR(255) PRIMARY KEY,
    solana_mint_address VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    total_supply BIGINT NOT NULL
);

CREATE TABLE orders (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL REFERENCES users(id),
    asset_id VARCHAR(255) NOT NULL REFERENCES assets(id),
    type VARCHAR(10) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    price DECIMAL(19, 4) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE trades_ledger (
   id VARCHAR(255) PRIMARY KEY,
   order_id VARCHAR(255) NOT NULL REFERENCES orders(id),
   transaction_hash VARCHAR(255),
   execution_price DECIMAL(19, 4) NOT NULL,
   timestamp TIMESTAMP NOT NULL
);