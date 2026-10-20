package com.billogram.dataprocessing.outbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private static final int BATCH_SIZE = 50;
    private static final OutboxMessage FIRST = new OutboxMessage(1, "topic-a", "key-1", "payload-1");
    private static final OutboxMessage SECOND = new OutboxMessage(2, "topic-a", "key-2", "payload-2");
    private static final OutboxMessage THIRD = new OutboxMessage(3, "topic-b", null, "payload-3");

    private final OutboxRepository repository = mock(OutboxRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final OutboxRelay relay = relayWithBatchSize(BATCH_SIZE);

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    private OutboxRelay relayWithBatchSize(int batchSize) {
        return new OutboxRelay(repository, kafkaTemplate,
                new OutboxProperties(Duration.ofSeconds(1), batchSize, Duration.ofMillis(50)), transactionManager);
    }

    private void givenPending(OutboxMessage... messages) {
        when(repository.lockOldest(BATCH_SIZE)).thenReturn(List.of(messages));
    }

    private void givenSendResult(OutboxMessage message, CompletableFuture<SendResult<String, String>> result) {
        when(kafkaTemplate.send(message.topic(), message.key(), message.payload())).thenReturn(result);
    }

    private static CompletableFuture<SendResult<String, String>> confirmed() {
        return CompletableFuture.completedFuture(null);
    }

    @Test
    void publishesPendingMessagesInOrderAndDeletesEachAfterConfirmation() {
        givenPending(FIRST, SECOND, THIRD);
        givenSendResult(FIRST, confirmed());
        givenSendResult(SECOND, confirmed());
        givenSendResult(THIRD, confirmed());

        relay.publishPending();

        InOrder order = inOrder(kafkaTemplate, repository);
        order.verify(kafkaTemplate).send("topic-a", "key-1", "payload-1");
        order.verify(repository).delete(1);
        order.verify(kafkaTemplate).send("topic-a", "key-2", "payload-2");
        order.verify(repository).delete(2);
        order.verify(kafkaTemplate).send("topic-b", null, "payload-3");
        order.verify(repository).delete(3);
    }

    @Test
    void stopsAtFirstFailedSendSoRemainingMessagesStayInOrder() {
        givenPending(FIRST, SECOND, THIRD);
        givenSendResult(FIRST, confirmed());
        givenSendResult(SECOND, CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        relay.publishPending();

        verify(repository).delete(1);
        verify(repository, never()).delete(2);
        verify(kafkaTemplate, never()).send(THIRD.topic(), THIRD.key(), THIRD.payload());
    }

    @Test
    void commitsDeletesOfSentMessagesWhenKafkaRejectsASendRightAway() {
        givenPending(FIRST, SECOND, THIRD);
        givenSendResult(FIRST, confirmed());
        when(kafkaTemplate.send(SECOND.topic(), SECOND.key(), SECOND.payload())).thenThrow(new KafkaException("Send failed"));

        relay.publishPending();

        verify(repository).delete(1);
        verify(repository, never()).delete(2);
        verify(kafkaTemplate, never()).send(THIRD.topic(), THIRD.key(), THIRD.payload());
        verify(transactionManager).commit(any());   // a rollback would undo delete(1), so FIRST would be sent again
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void keepsMessageWhenBrokerDoesNotConfirmInTime() {
        givenPending(FIRST);
        givenSendResult(FIRST, new CompletableFuture<>());   // never completes

        relay.publishPending();

        verify(repository, never()).delete(anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsMessageAndRestoresInterruptFlagWhenInterrupted() throws Exception {
        CompletableFuture<SendResult<String, String>> interrupted = mock(CompletableFuture.class);
        when(interrupted.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
        givenPending(FIRST, SECOND);
        givenSendResult(FIRST, interrupted);

        relay.publishPending();

        assertTrue(Thread.currentThread().isInterrupted());
        verify(repository, never()).delete(anyLong());
        verify(kafkaTemplate, never()).send(SECOND.topic(), SECOND.key(), SECOND.payload());
    }

    @Test
    void doesNothingWhenOutboxIsEmpty() {
        givenPending();

        relay.publishPending();

        verifyNoInteractions(kafkaTemplate);
        verify(repository, never()).delete(anyLong());
    }

    // --- draining a backlog; stubs end with an empty batch, so a relay that kept looping would fail, not hang

    @Test
    void publishesTheNextBatchRightAwayWhileBatchesAreFull() {
        when(repository.lockOldest(2)).thenReturn(List.of(FIRST, SECOND), List.of(THIRD), List.of());
        givenSendResult(FIRST, confirmed());
        givenSendResult(SECOND, confirmed());
        givenSendResult(THIRD, confirmed());

        relayWithBatchSize(2).publishPending();

        verify(repository, times(2)).lockOldest(2);
        InOrder order = inOrder(repository, transactionManager);
        order.verify(repository).delete(1);
        order.verify(repository).delete(2);
        order.verify(transactionManager).commit(any());   // each batch commits its own transaction
        order.verify(repository).delete(3);
        order.verify(transactionManager).commit(any());
    }

    @Test
    void waitsForTheNextRunOnceABatchIsNotFull() {
        when(repository.lockOldest(BATCH_SIZE)).thenReturn(List.of(FIRST), List.of());
        givenSendResult(FIRST, confirmed());

        relay.publishPending();

        verify(repository, times(1)).lockOldest(BATCH_SIZE);
    }

    @Test
    void waitsForTheNextRunWhenASendFailsEvenIfTheBatchWasFull() {
        when(repository.lockOldest(2)).thenReturn(List.of(FIRST, SECOND), List.of());
        givenSendResult(FIRST, confirmed());
        givenSendResult(SECOND, CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        relayWithBatchSize(2).publishPending();

        verify(repository, times(1)).lockOldest(2);
        verify(repository, never()).delete(2);
    }
}
