package com.panggilan.loket.service;

import com.panggilan.loket.model.Ticket;

public interface TicketAuditService {

    void recordIssued(Ticket ticket);

    void recordCalled(Ticket ticket);

    void recordCompleted(Ticket ticket, String counterId);

    void recordStopped(Ticket ticket, String counterId);

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
