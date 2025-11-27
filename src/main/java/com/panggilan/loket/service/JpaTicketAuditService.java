package com.panggilan.loket.service;

import com.panggilan.loket.entity.TicketEventEntity;
import com.panggilan.loket.model.PatientType;
import com.panggilan.loket.model.Ticket;
import com.panggilan.loket.model.TicketEventType;
import com.panggilan.loket.repository.TicketEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class JpaTicketAuditService implements TicketAuditService {

    private final TicketEventRepository repository;
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");

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

    @Override
    @Transactional(readOnly = true)
    public int loadLastSequenceForDate(LocalDate date) {
        if (repository == null || date == null) {
            return 0;
        }
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return repository.findTopByEventTypeAndEventTimeBetweenOrderByEventTimeDesc(
                        TicketEventType.ISSUED, start, end)
                .map(entity -> parseTicketNumber(entity.getTicketNumber()))
                .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public int loadLastSequenceForDate(LocalDate date, PatientType patientType) {
        // Delegate ke method tanpa patient type karena sequence sekarang shared
        return loadLastSequenceForDate(date);
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
                counterId, counterName, timestamp, ticket.getPatientType());
        repository.save(entity);
    }

    private int parseTicketNumber(String ticketNumber) {
        if (ticketNumber == null || ticketNumber.isBlank()) {
            return 0;
        }
        Matcher matcher = DIGIT_PATTERN.matcher(ticketNumber);
        if (!matcher.find()) {
            return 0;
        }
        String digits = matcher.group();
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
