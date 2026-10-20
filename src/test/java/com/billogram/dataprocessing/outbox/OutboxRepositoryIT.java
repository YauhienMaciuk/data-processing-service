package com.billogram.dataprocessing.outbox;

import com.billogram.dataprocessing.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each test runs in a transaction that is rolled back, so the running {@link OutboxRelay} never sees (or publishes)
 * the test's rows: they are uncommitted, and pending rows from other tests are deleted inside the transaction.
 */
@IntegrationTest
class OutboxRepositoryIT {

    @Autowired
    private OutboxRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
    }

    private void inRolledBackTransaction(Runnable test) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM outbox_events");
            test.run();
            status.setRollbackOnly();
        });
    }

    private static List<String> payloads(List<OutboxMessage> messages) {
        return messages.stream().map(OutboxMessage::payload).toList();
    }

    @Test
    void returnsOldestMessagesFirstUpToTheLimit() {
        inRolledBackTransaction(() -> {
            repository.add("topic-a", "key-1", "first");
            repository.add("topic-b", null, "second");
            repository.add("topic-a", "key-3", "third");

            List<OutboxMessage> oldest = repository.lockOldest(2);

            assertEquals(List.of("first", "second"), payloads(oldest));
            assertEquals("topic-a", oldest.get(0).topic());
            assertEquals("key-1", oldest.get(0).key());
            assertEquals("topic-b", oldest.get(1).topic());
            assertEquals(null, oldest.get(1).key());
        });
    }

    @Test
    void deleteRemovesOnlyThatMessage() {
        inRolledBackTransaction(() -> {
            repository.add("topic-a", "key-1", "first");
            repository.add("topic-a", "key-2", "second");
            long firstId = repository.lockOldest(1).getFirst().id();

            repository.delete(firstId);

            assertEquals(List.of("second"), payloads(repository.lockOldest(10)));
        });
    }
}
