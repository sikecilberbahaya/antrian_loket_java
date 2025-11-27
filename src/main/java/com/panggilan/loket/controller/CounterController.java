package com.panggilan.loket.controller;

import com.panggilan.loket.dto.CreateCounterRequest;
import com.panggilan.loket.model.CounterSnapshot;
import com.panggilan.loket.model.PatientType;
import com.panggilan.loket.model.QueueStatus;
import com.panggilan.loket.model.Ticket;
import com.panggilan.loket.service.QueueService;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CounterController {

    private final QueueService queueService;

    public CounterController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/counters")
    public List<CounterSnapshot> listCounters() {
        return queueService.getSnapshot();
    }

    @PostMapping("/counters")
    public ResponseEntity<CounterSnapshot> createCounter(@Valid @RequestBody CreateCounterRequest request) {
        CounterSnapshot snapshot = queueService.createCounter(request.getId(), request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(snapshot);
    }

    @PostMapping("/tickets")
    public ResponseEntity<Ticket> issueTicket(
            @RequestParam(value = "patientType", required = false) String patientTypeParam) {
        PatientType patientType = PatientType.fromString(patientTypeParam);
        Ticket ticket = queueService.issueTicket(patientType);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
    }

    @PostMapping("/queue/call-next")
    public ResponseEntity<?> callNext() {
        try {
            return queueService.callNextFirstCounter()
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/counters/{counterId}/call-next")
    public ResponseEntity<?> callNextForCounter(@PathVariable String counterId) {
        try {
            return queueService.callNext(counterId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/counters/{counterId}/recall")
    public ResponseEntity<?> recall(@PathVariable String counterId,
                                    @RequestParam(value = "ticketId", required = false) String ticketId) {
        try {
            return queueService.recall(counterId, ticketId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/counters/{counterId}/complete")
    public ResponseEntity<?> complete(@PathVariable String counterId,
                                      @RequestParam(value = "ticketId", required = false) String ticketId) {
        try {
            queueService.complete(counterId, ticketId);
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/counters/{counterId}/stop")
    public ResponseEntity<?> stop(@PathVariable String counterId,
                                  @RequestParam(value = "ticketId", required = false) String ticketId) {
        try {
            return queueService.stop(counterId, ticketId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/queue/status")
    public QueueStatus queueStatus() {
        return queueService.getQueueStatus();
    }

    @PostMapping("/queue/reset")
    public ResponseEntity<Void> resetQueue() {
        queueService.manualReset();
        return ResponseEntity.accepted().build();
    }
}
