-- Reviewed schema preparation for checkout and printable receipts. Run only after backup;
-- this file is not executed automatically by the application.
DELIMITER //

CREATE PROCEDURE pawconnect_add_checkout_column(IN table_name_in VARCHAR(64), IN column_name_in VARCHAR(64), IN ddl_in TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = table_name_in AND column_name = column_name_in
    ) THEN
        SET @pawconnect_checkout_ddl = ddl_in;
        PREPARE pawconnect_checkout_stmt FROM @pawconnect_checkout_ddl;
        EXECUTE pawconnect_checkout_stmt;
        DEALLOCATE PREPARE pawconnect_checkout_stmt;
    END IF;
END//

CALL pawconnect_add_checkout_column('orders', 'idempotency_key', 'ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(64) NULL')//
CALL pawconnect_add_checkout_column('orders', 'payment_method', 'ALTER TABLE orders ADD COLUMN payment_method VARCHAR(30) NULL')//
CALL pawconnect_add_checkout_column('orders', 'payment_status', 'ALTER TABLE orders ADD COLUMN payment_status VARCHAR(30) NULL')//
CALL pawconnect_add_checkout_column('orders', 'customer_name', 'ALTER TABLE orders ADD COLUMN customer_name VARCHAR(120) NULL')//
CALL pawconnect_add_checkout_column('orders', 'branch_name_snapshot', 'ALTER TABLE orders ADD COLUMN branch_name_snapshot VARCHAR(255) NULL')//
CALL pawconnect_add_checkout_column('orders', 'branch_address_snapshot', 'ALTER TABLE orders ADD COLUMN branch_address_snapshot VARCHAR(255) NULL')//
CALL pawconnect_add_checkout_column('orders', 'branch_phone_snapshot', 'ALTER TABLE orders ADD COLUMN branch_phone_snapshot VARCHAR(50) NULL')//
CALL pawconnect_add_checkout_column('orders', 'recipient_name', 'ALTER TABLE orders ADD COLUMN recipient_name VARCHAR(120) NULL')//
CALL pawconnect_add_checkout_column('orders', 'recipient_phone', 'ALTER TABLE orders ADD COLUMN recipient_phone VARCHAR(20) NULL')//
CALL pawconnect_add_checkout_column('orders', 'delivery_address', 'ALTER TABLE orders ADD COLUMN delivery_address VARCHAR(500) NULL')//
CALL pawconnect_add_checkout_column('order_items', 'item_name_snapshot', 'ALTER TABLE order_items ADD COLUMN item_name_snapshot VARCHAR(255) NULL')//
DROP PROCEDURE pawconnect_add_checkout_column//

CREATE PROCEDURE pawconnect_add_checkout_index()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'uk_orders_user_idempotency'
    ) THEN
        ALTER TABLE orders ADD CONSTRAINT uk_orders_user_idempotency UNIQUE (user_id, idempotency_key);
    END IF;
END//
CALL pawconnect_add_checkout_index()//
DROP PROCEDURE pawconnect_add_checkout_index//

DELIMITER ;

UPDATE order_items oi
LEFT JOIN products p ON p.id = oi.product_id
LEFT JOIN puppy_listings pl ON pl.id = oi.puppy_listing_id
SET oi.item_name_snapshot = COALESCE(p.name, pl.listing_title, 'Sản phẩm đã ngừng bán')
WHERE oi.item_name_snapshot IS NULL;

UPDATE orders o
LEFT JOIN users u ON u.id = o.user_id
LEFT JOIN branches b ON b.id = o.branch_id
SET o.customer_name = COALESCE(o.customer_name, u.full_name),
    o.branch_name_snapshot = COALESCE(o.branch_name_snapshot, b.name),
    o.branch_address_snapshot = COALESCE(o.branch_address_snapshot, b.address),
    o.branch_phone_snapshot = COALESCE(o.branch_phone_snapshot, b.phone)
WHERE o.customer_name IS NULL OR o.branch_name_snapshot IS NULL
   OR o.branch_address_snapshot IS NULL OR o.branch_phone_snapshot IS NULL;

CREATE TABLE IF NOT EXISTS order_status_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    payment_status VARCHAR(30) NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_order_status_events_order_time (order_id, created_at),
    CONSTRAINT fk_order_status_events_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
