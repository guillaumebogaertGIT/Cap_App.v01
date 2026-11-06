CREATE TABLE invoice_archive (
    id UUID PRIMARY KEY,
    client_id UUID REFERENCES cap_user(id),
    customer_name VARCHAR(160) NOT NULL,
    reference VARCHAR(80) NOT NULL,
    issued_on DATE NOT NULL,
    description VARCHAR(500) NOT NULL,
    document_type VARCHAR(16) NOT NULL CHECK (document_type IN ('invoice','credit_note')),
    gross_cents BIGINT NOT NULL CHECK (gross_cents BETWEEN -10000000 AND 10000000),
    net_cents BIGINT,
    payment_status VARCHAR(8) NOT NULL CHECK (payment_status IN ('open','paid')),
    filename VARCHAR(180) NOT NULL,
    size_bytes INTEGER NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
    data BYTEA NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    uploaded_by UUID NOT NULL REFERENCES cap_user(id),
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX invoice_reference_unique ON invoice_archive(LOWER(reference));
CREATE INDEX invoice_archive_date ON invoice_archive(issued_on);
CREATE TABLE invoice_archive_lock(id INTEGER PRIMARY KEY CHECK(id=1));
INSERT INTO invoice_archive_lock(id) VALUES(1);
