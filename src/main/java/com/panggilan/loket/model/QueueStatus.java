package com.panggilan.loket.model;

import java.util.Collections;
import java.util.List;

public final class QueueStatus {

    private final List<Ticket> waitingQueue;
    private final int nextTicketNumber;

    public QueueStatus(List<Ticket> waitingQueue, int nextTicketNumber) {
        this.waitingQueue = waitingQueue == null ? Collections.emptyList() : Collections.unmodifiableList(waitingQueue);
        this.nextTicketNumber = nextTicketNumber;
    }

    public List<Ticket> getWaitingQueue() {
        return waitingQueue;
    }

    public int getNextTicketNumber() {
        return nextTicketNumber;
    }
}
