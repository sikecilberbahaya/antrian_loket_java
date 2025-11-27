package com.panggilan.loket.service;

import com.panggilan.loket.model.PatientType;
import com.panggilan.loket.model.Ticket;

import java.time.LocalDate;

public interface TicketAuditService {

    void recordIssued(Ticket ticket);

    void recordCalled(Ticket ticket);

    void recordCompleted(Ticket ticket, String counterId);

    void recordStopped(Ticket ticket, String counterId);

    default int loadLastSequenceForDate(LocalDate date) {
        return loadLastSequenceForDate(date, PatientType.LAMA);
    }

    default int loadLastSequenceForDate(LocalDate date, PatientType patientType) {
        return 0;
    }

    static TicketAuditService noop() {
        return new TicketAuditService() {
            @Override
            public void recordIssued(Ticket ticket) {
            }

            @Override
            public void recordCalled(Ticket ticket) {
            }

            @Override
            public void recordCompleted(Ticket ticket, String counterId) {
            }

            @Override
            public void recordStopped(Ticket ticket, String counterId) {
            }
        };
    }
}
