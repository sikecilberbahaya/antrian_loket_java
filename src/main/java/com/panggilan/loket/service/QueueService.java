package com.panggilan.loket.service;

import com.panggilan.loket.config.CounterProperties;
import com.panggilan.loket.model.CounterSnapshot;
import com.panggilan.loket.model.QueueStatus;
import com.panggilan.loket.model.Ticket;
import java.time.LocalDateTime;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class QueueService {

    private final CounterProperties counterProperties;
    private final Map<String, CounterState> counters = new ConcurrentHashMap<>();
    private final Map<String, Deque<Ticket>> waitingByCounter = new ConcurrentHashMap<>();
    private final AtomicInteger ticketSequence = new AtomicInteger();
    private final CopyOnWriteArrayList<String> counterOrder = new CopyOnWriteArrayList<>();

    public QueueService(CounterProperties counterProperties) {
        this.counterProperties = counterProperties;
    }

    @PostConstruct
    void initializeCounters() {
        counterProperties.getCounters()
                .forEach(def -> registerCounter(def.getId(), def.getName()));
        if (counterOrder.isEmpty()) {
            registerCounter("A", "Loket A");
            registerCounter("B", "Loket B");
            registerCounter("C", "Loket C");
        }
    }

    public List<CounterSnapshot> getSnapshot() {
        int nextNumber = previewNextTicketNumber();
        return counterOrder.stream()
                .map(counters::get)
                .filter(Objects::nonNull)
                .map(state -> {
                    Deque<Ticket> queue = waitingByCounter.get(state.id);
                    List<Ticket> waiting = queue == null ? List.of() : List.copyOf(queue);
                    return state.snapshot(waiting, nextNumber);
                })
                .collect(Collectors.toList());
    }

    public synchronized CounterSnapshot createCounter(String id, String name) {
        Assert.hasText(id, "Counter id is required");
        Assert.hasText(name, "Counter name is required");
        CounterState state = registerCounter(id, name);
        Deque<Ticket> queue = waitingByCounter.get(state.id);
        List<Ticket> waiting = queue == null ? List.of() : List.copyOf(queue);
        return state.snapshot(waiting, previewNextTicketNumber());
    }

    public synchronized Ticket issueTicket() {
        String firstCounterId = firstCounterId();
        Assert.state(firstCounterId != null, "Tidak ada loket terdaftar");
        int nextSequence = ticketSequence.incrementAndGet();
        String ticketNumber = String.format("Q-%03d", nextSequence);
        Ticket ticket = Ticket.create(ticketNumber);
        waitingByCounter.get(firstCounterId).addLast(ticket);
        return ticket;
    }

    public synchronized Optional<Ticket> callNext(String counterId) {
        CounterState counter = requireCounter(counterId);
        if (counter.current != null) {
            throw new IllegalStateException(
                    "Loket " + counterId + " masih melayani nomor " + counter.current.getNumber());
        }
        Deque<Ticket> queue = waitingByCounter.get(counterId);
        if (queue == null) {
            return Optional.empty();
        }
        Ticket ticket = queue.pollFirst();
        if (ticket == null) {
            return Optional.empty();
        }
        Ticket assigned = ticket.assignToCounter(counter.id, counter.name);
        counter.current = assigned;
        counter.lastCalledAt = LocalDateTime.now();
        return Optional.of(assigned);
    }

    public synchronized Optional<Ticket> callNextFirstCounter() {
        String firstCounterId = firstCounterId();
        if (firstCounterId == null) {
            return Optional.empty();
        }
        return callNext(firstCounterId);
    }

    public Optional<Ticket> recall(String counterId) {
        CounterState counter = requireCounter(counterId);
        Ticket current = counter.current;
        if (current != null) {
            counter.lastCalledAt = LocalDateTime.now();
        }
        return Optional.ofNullable(current);
    }

    public synchronized void complete(String counterId) {
        CounterState counter = requireCounter(counterId);
        Ticket current = counter.current;
        if (current == null) {
            return;
        }
        counter.current = null;
        String nextCounterId = nextCounterId(counterId);
        if (nextCounterId != null) {
            waitingByCounter.get(nextCounterId).addLast(current.resetCounter());
        }
    }

    public List<Ticket> getWaitingQueue() {
        String firstCounterId = firstCounterId();
        if (firstCounterId == null) {
            return List.of();
        }
        Deque<Ticket> queue = waitingByCounter.get(firstCounterId);
        if (queue == null) {
            return List.of();
        }
        return List.copyOf(queue);
    }

    public int previewNextTicketNumber() {
        return ticketSequence.get() + 1;
    }

    public QueueStatus getQueueStatus() {
        return new QueueStatus(getWaitingQueue(), previewNextTicketNumber());
    }

    private synchronized CounterState registerCounter(String id, String name) {
        CounterState state = counters.compute(id, (key, existing) -> {
            if (existing == null) {
                return new CounterState(key, name);
            }
            existing.name = name;
            return existing;
        });
        waitingByCounter.computeIfAbsent(id, key -> new ConcurrentLinkedDeque<>());
        if (!counterOrder.contains(id)) {
            counterOrder.add(id);
        }
        return state;
    }

    private CounterState requireCounter(String counterId) {
        CounterState counter = counters.get(counterId);
        if (counter == null) {
            throw new IllegalArgumentException("Loket dengan id " + counterId + " tidak ditemukan");
        }
        return counter;
    }

    private String firstCounterId() {
        return counterOrder.isEmpty() ? null : counterOrder.get(0);
    }

    private String nextCounterId(String counterId) {
        int index = counterOrder.indexOf(counterId);
        if (index == -1) {
            return null;
        }
        int nextIndex = index + 1;
        if (nextIndex >= counterOrder.size()) {
            return null;
        }
        return counterOrder.get(nextIndex);
    }

    private static final class CounterState {
        private final String id;
        private volatile String name;
        private volatile Ticket current;
        private volatile LocalDateTime lastCalledAt;

        private CounterState(String id, String name) {
            this.id = id;
            this.name = name;
        }

        private CounterSnapshot snapshot(List<Ticket> waitingQueue, int nextNumber) {
            return new CounterSnapshot(id, name, current, waitingQueue, nextNumber, lastCalledAt);
        }
    }
}
