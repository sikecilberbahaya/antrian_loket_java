package com.panggilan.loket.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public final class Ticket {

    private final String id;
    private final String number;
    private final LocalDateTime issuedAt;
    private final String counterId;
    private final String counterName;
    private final LocalDate displayDate;
    private final PatientType patientType;

    private Ticket(String id, String number, LocalDateTime issuedAt, LocalDate displayDate, String counterId, String counterName, PatientType patientType) {
        this.id = id;
        this.number = number;
        this.issuedAt = issuedAt;
        this.displayDate = displayDate;
        this.counterId = counterId;
        this.counterName = counterName;
        this.patientType = patientType == null ? PatientType.LAMA : patientType;
    }

    public static Ticket create(String number) {
        return create(number, PatientType.LAMA);
    }

    public static Ticket create(String number, PatientType patientType) {
        LocalDate today = LocalDate.now();
        return new Ticket(UUID.randomUUID().toString(), number, LocalDateTime.now(), today, null, null, patientType);
    }

    public Ticket assignToCounter(String counterId, String counterName) {
        return new Ticket(id, number, issuedAt, displayDate, counterId, counterName, patientType);
    }

    public Ticket resetCounter() {
        return new Ticket(id, number, issuedAt, displayDate, null, null, patientType);
    }

    public String getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public String getCounterId() {
        return counterId;
    }

    public String getCounterName() {
        return counterName;
    }

    public LocalDate getDisplayDate() {
        return displayDate;
    }

    public PatientType getPatientType() {
        return patientType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ticket)) {
            return false;
        }
        Ticket ticket = (Ticket) o;
        return Objects.equals(id, ticket.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
