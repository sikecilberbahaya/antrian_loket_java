package com.panggilan.loket.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public final class Ticket {

    private final String id;
    private final String number;
    private final LocalDateTime issuedAt;
    private final String counterId;
    private final String counterName;

    private Ticket(String id, String number, LocalDateTime issuedAt, String counterId, String counterName) {
        this.id = id;
        this.number = number;
        this.issuedAt = issuedAt;
        this.counterId = counterId;
        this.counterName = counterName;
    }

    public static Ticket create(String number) {
        return new Ticket(UUID.randomUUID().toString(), number, LocalDateTime.now(), null, null);
    }

    public Ticket assignToCounter(String counterId, String counterName) {
        return new Ticket(id, number, issuedAt, counterId, counterName);
    }

    public Ticket resetCounter() {
        return new Ticket(id, number, issuedAt, null, null);
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
