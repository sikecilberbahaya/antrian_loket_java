package com.panggilan.loket.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public final class CounterSnapshot {

        private final String id;
        private final String name;
        private final Ticket currentTicket;
        private final List<Ticket> activeTickets;
        private final List<Ticket> waitingTickets;
        private final int nextNumber;
        private final LocalDateTime lastCalledAt;
        private final Ticket lastCalledTicket;

        public CounterSnapshot(String id, String name, List<Ticket> activeTickets, List<Ticket> waitingTickets,
                                                   int nextNumber, LocalDateTime lastCalledAt, Ticket lastCalledTicket) {
                this.id = id;
                this.name = name;
                List<Ticket> actives = activeTickets == null ? Collections.emptyList() : List.copyOf(activeTickets);
                this.activeTickets = actives;
                this.currentTicket = actives.isEmpty() ? null : actives.get(0);
                this.waitingTickets = waitingTickets == null ? Collections.emptyList() : Collections.unmodifiableList(waitingTickets);
                this.nextNumber = nextNumber;
                this.lastCalledAt = lastCalledAt;
                this.lastCalledTicket = lastCalledTicket;
        }

        public String getId() {
                return id;
        }

        public String getName() {
                return name;
        }

        public Ticket getCurrentTicket() {
                return currentTicket;
        }

        public List<Ticket> getActiveTickets() {
                return activeTickets;
        }

        public List<Ticket> getWaitingTickets() {
                return waitingTickets;
        }

        public int getNextNumber() {
                return nextNumber;
        }

        public LocalDateTime getLastCalledAt() {
                return lastCalledAt;
        }

        public Ticket getLastCalledTicket() {
                return lastCalledTicket;
        }
}
