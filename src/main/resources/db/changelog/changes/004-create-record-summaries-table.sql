--liquibase formatted sql

--changeset data-processing-service:record-summaries-001-create-table
-- Running summary of the records processed so far per destination and reference, updated in the same transaction
-- as each new record. Records without a reference share one summary per destination (NULLS NOT DISTINCT).
CREATE TABLE record_summaries (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    destination_id VARCHAR(255)             NOT NULL,
    reference      VARCHAR(255),
    record_count   BIGINT                   NOT NULL,
    totals         JSONB                    NOT NULL,  -- signed total per unit, e.g. {"EUR": 70.25, "kg": 2}
    first_time     TIMESTAMP WITH TIME ZONE,           -- NULL while record_count is 0
    last_time      TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_record_summaries                       PRIMARY KEY (id),
    CONSTRAINT uq_record_summaries_destination_reference UNIQUE NULLS NOT DISTINCT (destination_id, reference)
);
--rollback DROP TABLE record_summaries;
