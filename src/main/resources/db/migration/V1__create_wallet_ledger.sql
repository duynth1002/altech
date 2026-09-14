CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL UNIQUE,
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    type VARCHAR(10) NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    amount BIGINT NOT NULL CHECK (amount > 0),
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    reason VARCHAR(255) NOT NULL,
    reference_type VARCHAR(50),
    reference_id VARCHAR(100),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_wallet_idempotency UNIQUE (wallet_id, idempotency_key)
);

CREATE INDEX idx_wallet_transactions_history
    ON wallet_transactions (wallet_id, created_at DESC, id DESC);

-- Ledger rows are an audit trail. Application code never updates or deletes them;
-- this trigger is defense in depth for the same rule.
CREATE OR REPLACE FUNCTION prevent_wallet_transaction_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'wallet_transactions is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_wallet_transactions_no_update
    BEFORE UPDATE OR DELETE ON wallet_transactions
    FOR EACH ROW
    EXECUTE FUNCTION prevent_wallet_transaction_mutation();
