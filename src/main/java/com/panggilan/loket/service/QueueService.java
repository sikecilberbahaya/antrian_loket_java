package com.panggilan.loket.service;

import com.panggilan.loket.config.CounterProperties;
import com.panggilan.loket.model.CounterSnapshot;
import com.panggilan.loket.model.QueueStatus;
import com.panggilan.loket.model.Ticket;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final CounterProperties counterProperties;
    private final Clock clock;
    private final TicketPrinter ticketPrinter;
    private final TicketAuditService auditService;
    private final Map<String, CounterState> counters = new ConcurrentHashMap<>();
    private final Map<String, Deque<Ticket>> waitingByCounter = new ConcurrentHashMap<>();
    private final AtomicInteger ticketSequence = new AtomicInteger();
    private final CopyOnWriteArrayList<String> counterOrder = new CopyOnWriteArrayList<>();
    private volatile LocalDate lastResetDate;

    @Autowired
    public QueueService(CounterProperties counterProperties, TicketPrinter ticketPrinter, TicketAuditService auditService) {
        this(counterProperties, ticketPrinter, auditService, Clock.systemDefaultZone());
    }

    QueueService(CounterProperties counterProperties, TicketPrinter ticketPrinter, TicketAuditService auditService, Clock clock) {
        this.counterProperties = counterProperties;
        this.ticketPrinter = ticketPrinter == null ? TicketPrinter.noop() : ticketPrinter;
        this.auditService = auditService == null ? TicketAuditService.noop() : auditService;
        this.clock = clock;
        this.lastResetDate = LocalDate.now(clock);
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
        ensureDailyResetIfNeeded();
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
        ensureDailyResetIfNeeded();
        Assert.hasText(id, "Counter id is required");
        Assert.hasText(name, "Counter name is required");
        CounterState state = registerCounter(id, name);
        Deque<Ticket> queue = waitingByCounter.get(state.id);
        List<Ticket> waiting = queue == null ? List.of() : List.copyOf(queue);
        return state.snapshot(waiting, previewNextTicketNumber());
    }

    public synchronized Ticket issueTicket() {
        ensureDailyResetIfNeeded();
        String firstCounterId = firstCounterId();
        Assert.state(firstCounterId != null, "Tidak ada loket terdaftar");
        int nextSequence = ticketSequence.incrementAndGet();
        String ticketNumber = String.format("Q-%03d", nextSequence);
        Ticket ticket = Ticket.create(ticketNumber);
        waitingByCounter.get(firstCounterId).addLast(ticket);
        auditService.recordIssued(ticket);
        try {
            ticketPrinter.printTicket(ticket);
        } catch (Exception ex) {
            log.warn("Gagal memicu cetak tiket {}: {}", ticket.getNumber(), ex.getMessage());
        }
        return ticket;
    }

    public synchronized Optional<Ticket> callNext(String counterId) {
        ensureDailyResetIfNeeded();
        CounterState counter = requireCounter(counterId);
        if (counter.activeSize() >= 3) {
            throw new IllegalStateException("Loket " + counterId
                    + " sudah memanggil tiga nomor. Selesaikan salah satunya terlebih dahulu.");
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
        counter.addActive(assigned);
        counter.markLastCalled(assigned);
        auditService.recordCalled(assigned);
        return Optional.of(assigned);
    }

    public synchronized Optional<Ticket> callNextFirstCounter() {
        ensureDailyResetIfNeeded();
        String firstCounterId = firstCounterId();
        if (firstCounterId == null) {
            return Optional.empty();
        }
        return callNext(firstCounterId);
    }

    public Optional<Ticket> recall(String counterId) {
        return recall(counterId, null);
    }

    public Optional<Ticket> recall(String counterId, String ticketId) {
        ensureDailyResetIfNeeded();
        CounterState counter = requireCounter(counterId);
        Ticket target = counter.realignActiveTicket(ticketId);
        if (target == null && ticketId != null && !ticketId.isBlank()) {
            throw new IllegalArgumentException("Nomor " + ticketId + " tidak aktif di loket " + counterId);
        }
        if (target != null) {
            counter.markLastCalled(target);
            auditService.recordCalled(target);
        }
        return Optional.ofNullable(target);
    }

    public synchronized void complete(String counterId) {
        complete(counterId, null);
    }

    public synchronized void complete(String counterId, String ticketId) {
        ensureDailyResetIfNeeded();
        CounterState counter = requireCounter(counterId);
        Ticket current = counter.removeActive(ticketId);
        if (current == null && ticketId != null && !ticketId.isBlank()) {
            throw new IllegalArgumentException("Nomor " + ticketId + " tidak aktif di loket " + counterId);
        }
        if (current == null) {
            return;
        }
        counter.clearLastCalledIfMatches(current);
        auditService.recordCompleted(current, counterId);
        String nextCounterId = nextCounterId(counterId);
        if (nextCounterId != null) {
            waitingByCounter.get(nextCounterId).addLast(current.resetCounter());
        }
    }

    public Optional<Ticket> stop(String counterId) {
        return stop(counterId, null);
    }

    public synchronized Optional<Ticket> stop(String counterId, String ticketId) {
        ensureDailyResetIfNeeded();
        CounterState counter = requireCounter(counterId);
        Ticket removed = counter.removeActive(ticketId);
        if (removed == null && ticketId != null && !ticketId.isBlank()) {
            throw new IllegalArgumentException("Nomor " + ticketId + " tidak aktif di loket " + counterId);
        }
        if (removed == null) {
            return Optional.empty();
        }
        counter.clearLastCalledIfMatches(removed);
        auditService.recordStopped(removed, counterId);
        return Optional.of(removed);
    }

    public List<Ticket> getWaitingQueue() {
        ensureDailyResetIfNeeded();
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
        ensureDailyResetIfNeeded();
        return ticketSequence.get() + 1;
    }

    public QueueStatus getQueueStatus() {
        ensureDailyResetIfNeeded();
        return new QueueStatus(getWaitingQueue(), previewNextTicketNumber());
    }

    private void ensureDailyResetIfNeeded() {
        LocalDate today = LocalDate.now(clock);
        if (today.equals(lastResetDate)) {
            return;
        }
        synchronized (this) {
            if (today.equals(lastResetDate)) {
                return;
            }
            ticketSequence.set(0);
            waitingByCounter.values().forEach(Deque::clear);
            counters.values().forEach(state -> {
                state.clearActive();
            });
            lastResetDate = today;
        }
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
    private volatile LocalDateTime lastCalledAt;
    private volatile Ticket lastCalledTicket;

        private CounterState(String id, String name) {
            this.id = id;
            this.name = name;
        }

        private CounterSnapshot snapshot(List<Ticket> waitingQueue, int nextNumber) {
            List<Ticket> actives = new ArrayList<>(activeTickets);
            return new CounterSnapshot(id, name, actives, waitingQueue, nextNumber, lastCalledAt, lastCalledTicket);
        }

        private final Deque<Ticket> activeTickets = new ArrayDeque<>();
        private void addActive(Ticket ticket) {
            activeTickets.addLast(ticket);
        }

        private int activeSize() {
            return activeTickets.size();
        }

        private void clearActive() {
            activeTickets.clear();
            lastCalledTicket = null;
            lastCalledAt = null;
        }

        private Ticket realignActiveTicket(String ticketId) {
            if (ticketId == null || ticketId.isBlank()) {
                return activeTickets.peekLast();
            }
            if (activeTickets.isEmpty()) {
                return null;
            }
            Ticket found = null;
            List<Ticket> snapshot = new ArrayList<>(activeTickets);
            activeTickets.clear();
            for (Ticket ticket : snapshot) {
                if (found == null && ticketId.equals(ticket.getId())) {
                    found = ticket;
                    continue;
                }
                activeTickets.addLast(ticket);
            }
            if (found != null) {
                activeTickets.addLast(found);
            }
            return found;
        }

        private void markLastCalled(Ticket ticket) {
            if (ticket == null) {
                return;
            }
            lastCalledTicket = ticket;
            lastCalledAt = LocalDateTime.now();
        }

        private void clearLastCalledIfMatches(Ticket ticket) {
            if (ticket == null) {
                return;
            }
            if (lastCalledTicket != null && lastCalledTicket.equals(ticket)) {
                lastCalledTicket = null;
                lastCalledAt = null;
            }
        }

        private Ticket removeActive(String ticketId) {
            if (ticketId == null || ticketId.isBlank()) {
                return activeTickets.pollFirst();
            }
            Iterator<Ticket> iterator = activeTickets.iterator();
            while (iterator.hasNext()) {
                Ticket ticket = iterator.next();
                if (ticketId.equals(ticket.getId())) {
                    iterator.remove();
                    return ticket;
                }
            }
            return null;
        }
    }
}
