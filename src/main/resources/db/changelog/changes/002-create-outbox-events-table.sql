--liquibase formatted sql

--changeset data-processing-service:outbox-events-001-create-table
-- Transactional outbox: messages written in the same transaction as the business change,
-- then published to Kafka by OutboxRelay and deleted once the broker confirms
CREATE TABLE outbox_events (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    topic       VARCHAR(255)             NOT NULL,
    message_key VARCHAR(255),
    payload     TEXT                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);
--rollback DROP TABLE outbox_events;
