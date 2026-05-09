--liquibase formatted sql

--changeset initial_auth:1
CREATE TABLE IF NOT EXISTS users (
   id VARCHAR(255) PRIMARY KEY,
   wallet_address VARCHAR(255) NOT NULL UNIQUE,
   kyc_status VARCHAR(50) NOT NULL,
   aml_risk_score INT DEFAULT 0,
   password VARCHAR(255),
   is_qualified BOOLEAN DEFAULT FALSE
);

--changeset add_is_frozen:2
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_frozen BOOLEAN DEFAULT FALSE;
