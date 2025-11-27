package com.panggilan.loket.service;

import com.panggilan.loket.config.CounterProperties;
import com.panggilan.loket.model.PatientType;
import com.panggilan.loket.model.Ticket;
import com.panggilan.loket.service.TicketAuditService;
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
    queueService = new QueueService(properties, TicketPrinter.noop(), TicketAuditService.noop());
        queueService.initializeCounters();
    }

    @Test
    void issueTicketShouldAddToFirstCounterQueue() {
        Ticket ticket = queueService.issueTicket();
        assertThat(ticket.getNumber()).isEqualTo("L-001");
        assertThat(ticket.getPatientType()).isEqualTo(PatientType.LAMA);
        assertThat(queueService.getWaitingQueue()).hasSize(1);
    }

    @Test
    void ticketProgressesThroughCountersSequentially() {
        // Gunakan Pasien Baru agar bisa dipanggil di Loket A
        queueService.issueTicket(PatientType.BARU);

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
        // Gunakan Pasien Baru agar bisa dipanggil di Loket A
        queueService.issueTicket(PatientType.BARU);
        queueService.issueTicket(PatientType.BARU);
        queueService.issueTicket(PatientType.BARU);
        queueService.issueTicket(PatientType.BARU);

        queueService.callNext("A").orElseThrow();
        queueService.callNext("A").orElseThrow();
        queueService.callNext("A").orElseThrow();

        assertThatThrownBy(() -> queueService.callNext("A"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recallShouldReturnCurrentTicket() {
        // Gunakan Pasien Baru agar bisa dipanggil di Loket A
        queueService.issueTicket(PatientType.BARU);
        queueService.issueTicket(PatientType.BARU);

        queueService.callNext("A").orElseThrow();
        Ticket second = queueService.callNext("A").orElseThrow();

        Ticket recall = queueService.recall("A").orElseThrow();

        assertThat(recall.getId()).isEqualTo(second.getId());
    }

    @Test
    void completeRemovesOldestActiveTicketFirst() {
        // Gunakan Pasien Baru agar bisa dipanggil di Loket A
        queueService.issueTicket(PatientType.BARU);
        queueService.issueTicket(PatientType.BARU);
        queueService.issueTicket(PatientType.BARU);

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
    void stopShouldRemoveTicketWithoutForwarding() {
        // Gunakan Pasien Baru agar bisa dipanggil di Loket A
        queueService.issueTicket(PatientType.BARU);
        queueService.issueTicket(PatientType.BARU);

        Ticket first = queueService.callNext("A").orElseThrow();
        Ticket second = queueService.callNext("A").orElseThrow();

        Ticket stopped = queueService.stop("A", first.getId()).orElseThrow();
        assertThat(stopped.getId()).isEqualTo(first.getId());

        assertThat(queueService.callNext("B")).isEmpty();

        Ticket remaining = queueService.recall("A").orElseThrow();
        assertThat(remaining.getId()).isEqualTo(second.getId());
    }

    @Test
    void ticketSequenceResetsAtMidnight() throws Exception {
        queueService.issueTicket();
        queueService.issueTicket();
        assertThat(queueService.previewNextTicketNumber(PatientType.LAMA)).isEqualTo(3);

        Field lastResetField = QueueService.class.getDeclaredField("lastResetDate");
        lastResetField.setAccessible(true);
        lastResetField.set(queueService, LocalDate.now().minusDays(1));

        Ticket ticketAfterReset = queueService.issueTicket();

        assertThat(ticketAfterReset.getNumber()).isEqualTo("L-001");
        assertThat(queueService.getWaitingQueue()).hasSize(1);
        assertThat(queueService.previewNextTicketNumber(PatientType.LAMA)).isEqualTo(2);
    }

    @Test
    void manualResetClearsAllQueuesAndActiveTickets() {
        queueService.issueTicket();
        queueService.issueTicket();

        queueService.callNext("A");

        queueService.manualReset();

        assertThat(queueService.getWaitingQueue()).isEmpty();
        queueService.getSnapshot().forEach(snapshot -> {
            assertThat(snapshot.getActiveTickets()).isEmpty();
            assertThat(snapshot.getWaitingTickets()).isEmpty();
        });
        assertThat(queueService.previewNextTicketNumber(PatientType.LAMA)).isEqualTo(1);
    }

    @Test
    void issueTicketBaruShouldUseSharedSequence() {
        Ticket lamaTicket = queueService.issueTicket(PatientType.LAMA);
        Ticket baruTicket = queueService.issueTicket(PatientType.BARU);
        Ticket lamaTicket2 = queueService.issueTicket(PatientType.LAMA);

        assertThat(lamaTicket.getNumber()).isEqualTo("L-001");
        assertThat(lamaTicket.getPatientType()).isEqualTo(PatientType.LAMA);

        // Shared sequence: B-002 (bukan B-001)
        assertThat(baruTicket.getNumber()).isEqualTo("B-002");
        assertThat(baruTicket.getPatientType()).isEqualTo(PatientType.BARU);

        assertThat(lamaTicket2.getNumber()).isEqualTo("L-003");
        assertThat(lamaTicket2.getPatientType()).isEqualTo(PatientType.LAMA);

        assertThat(queueService.getWaitingQueue()).hasSize(3);
    }

    @Test
    void loketACanOnlyCallPasienBaru() {
        // Issue Pasien Lama dan Pasien Baru
        Ticket lama1 = queueService.issueTicket(PatientType.LAMA);
        Ticket baru1 = queueService.issueTicket(PatientType.BARU);
        Ticket lama2 = queueService.issueTicket(PatientType.LAMA);

        // Loket A hanya bisa memanggil Pasien Baru
        Ticket calledAtA = queueService.callNext("A").orElseThrow();
        assertThat(calledAtA.getId()).isEqualTo(baru1.getId());
        assertThat(calledAtA.getPatientType()).isEqualTo(PatientType.BARU);

        // Pasien Lama masih di antrian
        assertThat(queueService.getWaitingQueue()).hasSize(2);
        assertThat(queueService.getWaitingQueue().get(0).getId()).isEqualTo(lama1.getId());
        assertThat(queueService.getWaitingQueue().get(1).getId()).isEqualTo(lama2.getId());
    }

    @Test
    void loketBCanCallAnyPatientType() {
        // Issue Pasien Lama - akan masuk ke queue Loket A (first counter)
        Ticket lama1 = queueService.issueTicket(PatientType.LAMA);

        // Loket A tidak bisa memanggil Pasien Lama
        assertThat(queueService.callNext("A")).isEmpty();

        // Loket B bisa mengambil Pasien Lama dari queue Loket A
        Ticket calledAtB = queueService.callNext("B").orElseThrow();
        assertThat(calledAtB.getId()).isEqualTo(lama1.getId());
        assertThat(calledAtB.getPatientType()).isEqualTo(PatientType.LAMA);
    }
}
