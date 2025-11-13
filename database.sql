USE your_schema;

CREATE TABLE ticket_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id VARCHAR(64) NOT NULL,
    ticket_number VARCHAR(16) NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    counter_id VARCHAR(32),
    counter_name VARCHAR(128),
    event_time DATETIME NOT NULL,
    INDEX idx_ticket_events_ticket (ticket_id),
    INDEX idx_ticket_events_type_time (event_type, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;