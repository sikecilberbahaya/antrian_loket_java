package com.panggilan.loket.entity;

import com.panggilan.loket.model.PatientType;
import com.panggilan.loket.model.TicketEventType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_events")
public class TicketEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, length = 64)
    private String ticketId;

    @Column(name = "ticket_number", nullable = false, length = 16)
    private String ticketNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16)
    private TicketEventType eventType;

    @Column(name = "counter_id", length = 32)
    private String counterId;

    @Column(name = "counter_name", length = 128)
    private String counterName;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_type", length = 16)
    private PatientType patientType;

    protected TicketEventEntity() {
    }

    private TicketEventEntity(String ticketId,
                              String ticketNumber,
                              TicketEventType eventType,
                              String counterId,
                              String counterName,
                              LocalDateTime eventTime,
                              PatientType patientType) {
        this.ticketId = ticketId;
        this.ticketNumber = ticketNumber;
        this.eventType = eventType;
        this.counterId = counterId;
        this.counterName = counterName;
        this.eventTime = eventTime;
        this.patientType = patientType;
    }

    public static TicketEventEntity of(String ticketId,
                                       String ticketNumber,
                                       TicketEventType eventType,
                                       String counterId,
                                       String counterName,
                                       LocalDateTime eventTime) {
        return of(ticketId, ticketNumber, eventType, counterId, counterName, eventTime, null);
    }

    public static TicketEventEntity of(String ticketId,
                                       String ticketNumber,
                                       TicketEventType eventType,
                                       String counterId,
                                       String counterName,
                                       LocalDateTime eventTime,
                                       PatientType patientType) {
        return new TicketEventEntity(ticketId, ticketNumber, eventType, counterId, counterName, eventTime, patientType);
    }

    public Long getId() {
        return id;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public TicketEventType getEventType() {
        return eventType;
    }

    public String getCounterId() {
        return counterId;
    }

    public String getCounterName() {
        return counterName;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public PatientType getPatientType() {
        return patientType;
    }
}
