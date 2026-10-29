-- MySQL 8. Run on the selected PawConnect database only AFTER a verified backup.
-- Additive and repeatable; never drops orders/users or rewrites historical totals.
-- The application does not execute this script automatically.
CREATE TABLE IF NOT EXISTS discount_coupons (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    eligibility ENUM('COMPLETED_SPA','FIRST_ORDER','ORDER_MINIMUM') NOT NULL,
    discount_type ENUM('FIXED','PERCENT') NOT NULL,
    discount_value DECIMAL(19,2) NOT NULL,
    minimum_subtotal DECIMAL(19,2) NOT NULL,
    uses_per_customer INT NOT NULL,
    active BIT NOT NULL
);

DELIMITER //
CREATE PROCEDURE pawconnect_add_coupon_column(IN column_name_in VARCHAR(64), IN ddl_in TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = column_name_in) THEN
        SET @pawconnect_coupon_ddl = ddl_in;
        PREPARE pawconnect_coupon_stmt FROM @pawconnect_coupon_ddl;
        EXECUTE pawconnect_coupon_stmt;
        DEALLOCATE PREPARE pawconnect_coupon_stmt;
    END IF;
END//
CALL pawconnect_add_coupon_column('subtotal_amount', 'ALTER TABLE orders ADD COLUMN subtotal_amount DECIMAL(19,2) NULL')//
CALL pawconnect_add_coupon_column('discount_amount', 'ALTER TABLE orders ADD COLUMN discount_amount DECIMAL(19,2) NULL')//
CALL pawconnect_add_coupon_column('coupon_id', 'ALTER TABLE orders ADD COLUMN coupon_id BIGINT NULL')//
CALL pawconnect_add_coupon_column('coupon_code', 'ALTER TABLE orders ADD COLUMN coupon_code VARCHAR(40) NULL')//
CALL pawconnect_add_coupon_column('coupon_name', 'ALTER TABLE orders ADD COLUMN coupon_name VARCHAR(160) NULL')//
DROP PROCEDURE pawconnect_add_coupon_column//
CREATE PROCEDURE pawconnect_add_coupon_index()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'idx_orders_customer_coupon_status') THEN
        CREATE INDEX idx_orders_customer_coupon_status ON orders(user_id, coupon_id, status);
    END IF;
END//
CALL pawconnect_add_coupon_index()//
DROP PROCEDURE pawconnect_add_coupon_index//
DELIMITER ;

-- Built-in campaigns are inserted by DiscountCouponInitializer only when absent.
-- Existing campaigns (including inactive ones) and old orders remain unchanged.
