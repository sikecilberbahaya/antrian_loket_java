package com.panggilan.loket.service;

import com.panggilan.loket.config.CounterProperties;
import com.panggilan.loket.model.Ticket;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueServiceTests {

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        CounterProperties properties = new CounterProperties();
        CounterProperties.CounterDefinition definitionA = new CounterProperties.CounterDefinition();
        definitionA.setId("A");
        definitionA.setName("Loket A");
        CounterProperties.CounterDefinition definitionB = new CounterProperties.CounterDefinition();
        definitionB.setId("B");
        definitionB.setName("Loket B");
        CounterProperties.CounterDefinition definitionC = new CounterProperties.CounterDefinition();
        definitionC.setId("C");
        definitionC.setName("Loket C");
        properties.setCounters(List.of(definitionA, definitionB, definitionC));
        queueService = new QueueService(properties, TicketPrinter.noop());
        queueService.initializeCounters();
    }

    @Test
    void issueTicketShouldAddToFirstCounterQueue() {
        Ticket ticket = queueService.issueTicket();
        assertThat(ticket.getNumber()).isEqualTo("Q-001");
        assertThat(queueService.getWaitingQueue()).hasSize(1);
    }

    @Test
    void ticketProgressesThroughCountersSequentially() {
        queueService.issueTicket();

        Ticket atA = queueService.callNext("A").orElseThrow();
        assertThat(atA.getCounterId()).isEqualTo("A");
        assertThat(queueService.callNext("B")).isEmpty();

        queueService.complete("A");

        Ticket atB = queueService.callNext("B").orElseThrow();
        assertThat(atB.getId()).isEqualTo(atA.getId());
        assertThat(atB.getCounterId()).isEqualTo("B");

        queueService.complete("B");

        Ticket atC = queueService.callNext("C").orElseThrow();
        assertThat(atC.getId()).isEqualTo(atA.getId());
        assertThat(atC.getCounterId()).isEqualTo("C");
    }

    @Test
    void callNextWhileBusyShouldThrow() {
        queueService.issueTicket();
        queueService.issueTicket();
        queueService.issueTicket();
        queueService.issueTicket();

        queueService.callNext("A").orElseThrow();
        queueService.callNext("A").orElseThrow();
        queueService.callNext("A").orElseThrow();

        assertThatThrownBy(() -> queueService.callNext("A"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recallShouldReturnCurrentTicket() {
        queueService.issueTicket();
        queueService.issueTicket();

        queueService.callNext("A").orElseThrow();
        Ticket second = queueService.callNext("A").orElseThrow();

        Ticket recall = queueService.recall("A").orElseThrow();

        assertThat(recall.getId()).isEqualTo(second.getId());
    }

    @Test
    void completeRemovesOldestActiveTicketFirst() {
        queueService.issueTicket();
        queueService.issueTicket();
        queueService.issueTicket();

        Ticket first = queueService.callNext("A").orElseThrow();
        Ticket second = queueService.callNext("A").orElseThrow();

        queueService.complete("A");

        Ticket atB = queueService.callNext("B").orElseThrow();
        assertThat(atB.getId()).isEqualTo(first.getId());

        queueService.complete("B");

        Ticket remainingAtA = queueService.recall("A").orElseThrow();
        assertThat(remainingAtA.getId()).isEqualTo(second.getId());
    }

    @Test
    void ticketSequenceResetsAtMidnight() throws Exception {
        queueService.issueTicket();
        queueService.issueTicket();
        assertThat(queueService.previewNextTicketNumber()).isEqualTo(3);

        Field lastResetField = QueueService.class.getDeclaredField("lastResetDate");
        lastResetField.setAccessible(true);
        lastResetField.set(queueService, LocalDate.now().minusDays(1));

        Ticket ticketAfterReset = queueService.issueTicket();

        assertThat(ticketAfterReset.getNumber()).isEqualTo("Q-001");
        assertThat(queueService.getWaitingQueue()).hasSize(1);
        assertThat(queueService.previewNextTicketNumber()).isEqualTo(2);
    }
}
