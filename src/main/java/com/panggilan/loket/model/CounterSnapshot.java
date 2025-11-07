package com.panggilan.loket.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public final class CounterSnapshot {

        private final String id;
        private final String name;
        private final Ticket currentTicket;
        private final List<Ticket> waitingTickets;
        private final int nextNumber;
        private final LocalDateTime lastCalledAt;

        public CounterSnapshot(String id, String name, Ticket currentTicket, List<Ticket> waitingTickets, int nextNumber,
                        LocalDateTime lastCalledAt) {
                this.id = id;
                this.name = name;
                this.currentTicket = currentTicket;
                this.waitingTickets = waitingTickets == null ? Collections.emptyList() : Collections.unmodifiableList(waitingTickets);
                this.nextNumber = nextNumber;
                this.lastCalledAt = lastCalledAt;
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

        public List<Ticket> getWaitingTickets() {
                return waitingTickets;
        }

        public int getNextNumber() {
                return nextNumber;
        }

        public LocalDateTime getLastCalledAt() {
                return lastCalledAt;
        }
}
