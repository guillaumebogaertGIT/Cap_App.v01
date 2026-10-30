-- No real staff mapping is inferred. Explicit account-ID activation is a separate operation.
ALTER TABLE cap_user ADD COLUMN staff_role VARCHAR(20) NOT NULL DEFAULT 'UNASSIGNED'
    CHECK (staff_role IN ('UNASSIGNED','OWNER','MANAGER','INSTRUCTOR','FINANCE','DEVELOPMENT'));
CREATE TABLE instructor_client (
    instructor_id UUID NOT NULL REFERENCES cap_user(id),
    client_id UUID NOT NULL REFERENCES cap_user(id),
    granted_by UUID NOT NULL REFERENCES cap_user(id),
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (instructor_id,client_id)
);
CREATE TABLE staff_audit (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES cap_user(id),
    action VARCHAR(60) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    detail VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE staff_note (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES cap_user(id),
    assignment_id VARCHAR(100),
    author_id UUID NOT NULL REFERENCES cap_user(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    content VARCHAR(4000) NOT NULL,
    request_id UUID NOT NULL,
    UNIQUE(author_id,request_id)
);
CREATE INDEX staff_note_client ON staff_note(client_id,created_at);
CREATE TABLE assignment_attribution (
    assignment_id VARCHAR(100) PRIMARY KEY,
    workout_id VARCHAR(100) NOT NULL,
    client_id UUID NOT NULL REFERENCES cap_user(id),
    assigned_by UUID NOT NULL REFERENCES cap_user(id),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL
);
