package com.panggilan.loket.repository;

import com.panggilan.loket.entity.TicketEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketEventRepository extends JpaRepository<TicketEventEntity, Long> {
}
