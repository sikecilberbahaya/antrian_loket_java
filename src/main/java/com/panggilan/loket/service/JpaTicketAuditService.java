package com.panggilan.loket.service;

import com.panggilan.loket.entity.TicketEventEntity;
import com.panggilan.loket.model.Ticket;
import com.panggilan.loket.model.TicketEventType;
import com.panggilan.loket.repository.TicketEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@Transactional
public class JpaTicketAuditService implements TicketAuditService {

    private final TicketEventRepository repository;

    public JpaTicketAuditService(TicketEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void recordIssued(Ticket ticket) {
        persistEvent(ticket, TicketEventType.ISSUED, ticket == null ? null : ticket.getCounterId(), ticket == null ? null : ticket.getCounterName(),
                ticket != null ? ticket.getIssuedAt() : LocalDateTime.now());
    }

    @Override
    public void recordCalled(Ticket ticket) {
        persistEvent(ticket, TicketEventType.CALLED,
                ticket == null ? null : ticket.getCounterId(),
                ticket == null ? null : ticket.getCounterName(),
                LocalDateTime.now());
    }

    @Override
    public void recordCompleted(Ticket ticket, String counterId) {
        persistEvent(ticket, TicketEventType.COMPLETED, counterId,
                ticket == null ? null : ticket.getCounterName(), LocalDateTime.now());
    }

    @Override
    public void recordStopped(Ticket ticket, String counterId) {
        persistEvent(ticket, TicketEventType.STOPPED, counterId,
                ticket == null ? null : ticket.getCounterName(), LocalDateTime.now());
    }

    private void persistEvent(Ticket ticket,
                              TicketEventType type,
                              String counterId,
                              String counterName,
                              LocalDateTime eventTime) {
        if (repository == null || ticket == null) {
            return;
        }
        LocalDateTime timestamp = Objects.requireNonNullElse(eventTime, LocalDateTime.now());
        TicketEventEntity entity = TicketEventEntity.of(ticket.getId(), ticket.getNumber(), type,
                counterId, counterName, timestamp);
        repository.save(entity);
    }
}
