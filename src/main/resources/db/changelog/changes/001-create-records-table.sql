--liquibase formatted sql

--changeset data-processing-service:records-001-create-table
CREATE TABLE records (
    record_id      VARCHAR(255)             NOT NULL,  -- producer-supplied; the primary key, so a record is never processed twice
    time           TIMESTAMP WITH TIME ZONE NOT NULL,
    source_id      VARCHAR(255)             NOT NULL,
    destination_id VARCHAR(255)             NOT NULL,
    type           VARCHAR(16)              NOT NULL,
    value          NUMERIC                  NOT NULL,  -- unconstrained NUMERIC stores the decimal exactly, without rounding
    unit           VARCHAR(64)              NOT NULL,
    reference      VARCHAR(255),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_records      PRIMARY KEY (record_id),
    CONSTRAINT ck_records_type CHECK (type IN ('positive', 'negative'))
);
--rollback DROP TABLE records;
