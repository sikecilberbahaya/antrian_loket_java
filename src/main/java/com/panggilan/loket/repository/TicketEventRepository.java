package com.panggilan.loket.repository;

import com.panggilan.loket.entity.TicketEventEntity;
import com.panggilan.loket.model.PatientType;
import com.panggilan.loket.model.TicketEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TicketEventRepository extends JpaRepository<TicketEventEntity, Long> {

	Optional<TicketEventEntity> findTopByEventTypeAndEventTimeBetweenOrderByEventTimeDesc(
			TicketEventType eventType,
			LocalDateTime start,
			LocalDateTime end);

	Optional<TicketEventEntity> findTopByEventTypeAndPatientTypeAndEventTimeBetweenOrderByEventTimeDesc(
			TicketEventType eventType,
			PatientType patientType,
			LocalDateTime start,
			LocalDateTime end);
}
