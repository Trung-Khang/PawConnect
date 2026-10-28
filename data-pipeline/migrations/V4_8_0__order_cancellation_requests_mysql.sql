-- Run only after backup and owner approval; not executed automatically by the application.
-- No existing order statuses are rewritten: PAID, CONFIRMED and PREPARING remain readable
-- as legacy processing states in application code, preserving their audit history.
CREATE TABLE IF NOT EXISTS order_cancellation_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    resolved_by BIGINT NULL,
    decision_reason VARCHAR(500) NULL,
    INDEX idx_order_cancellation_requests_order_time (order_id, requested_at),
    CONSTRAINT fk_order_cancellation_requests_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
