-- PawConnect Phase C conversation queue migration.
-- Run once against the target `pawconnect` schema after taking a backup.
-- This preserves legacy adoption conversations: their former admin_id becomes
-- assigned_staff_id and they are marked CLAIMED. New conversations begin OPEN.

DELIMITER //

CREATE PROCEDURE pawconnect_migrate_conversation_queue()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'conversations'
    ) THEN
        CREATE TABLE conversations (
            id BIGINT NOT NULL AUTO_INCREMENT,
            customer_id BIGINT NOT NULL,
            assigned_staff_id BIGINT NULL,
            branch_id BIGINT NULL,
            type VARCHAR(20) NOT NULL,
            status VARCHAR(20) NOT NULL,
            adoption_post_id BIGINT NULL,
            product_id BIGINT NULL,
            puppy_listing_id BIGINT NULL,
            assigned_at DATETIME(6) NULL,
            last_message_at DATETIME(6) NOT NULL,
            created_at DATETIME(6) NOT NULL,
            PRIMARY KEY (id),
            CONSTRAINT fk_conversation_customer FOREIGN KEY (customer_id) REFERENCES users(id),
            CONSTRAINT fk_conversation_assigned_staff FOREIGN KEY (assigned_staff_id) REFERENCES users(id),
            CONSTRAINT fk_conversation_branch FOREIGN KEY (branch_id) REFERENCES branches(id),
            CONSTRAINT uk_conversation_adoption_customer_post UNIQUE (customer_id, adoption_post_id),
            INDEX idx_conversation_customer (customer_id),
            INDEX idx_conversation_branch_status (branch_id, status)
        );
    ELSE
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'conversations' AND column_name = 'admin_id'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'conversations' AND column_name = 'assigned_staff_id'
        ) THEN
            ALTER TABLE conversations CHANGE COLUMN admin_id assigned_staff_id BIGINT NULL;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'conversations' AND column_name = 'type') THEN
            ALTER TABLE conversations ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'ADOPTION';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'conversations' AND column_name = 'status') THEN
            ALTER TABLE conversations ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'conversations' AND column_name = 'branch_id') THEN
            ALTER TABLE conversations ADD COLUMN branch_id BIGINT NULL;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'conversations' AND column_name = 'product_id') THEN
            ALTER TABLE conversations ADD COLUMN product_id BIGINT NULL;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'conversations' AND column_name = 'puppy_listing_id') THEN
            ALTER TABLE conversations ADD COLUMN puppy_listing_id BIGINT NULL;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'conversations' AND column_name = 'assigned_at') THEN
            ALTER TABLE conversations ADD COLUMN assigned_at DATETIME(6) NULL;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'conversations' AND column_name = 'last_message_at') THEN
            ALTER TABLE conversations ADD COLUMN last_message_at DATETIME(6) NULL;
        END IF;

        ALTER TABLE conversations MODIFY COLUMN adoption_post_id BIGINT NULL;

        UPDATE conversations
        SET type = COALESCE(type, 'ADOPTION'),
            status = CASE WHEN assigned_staff_id IS NULL THEN 'OPEN' ELSE 'CLAIMED' END,
            assigned_at = CASE WHEN assigned_staff_id IS NULL THEN NULL ELSE COALESCE(assigned_at, created_at) END;

        UPDATE conversations c
        JOIN adoption_posts ap ON ap.id = c.adoption_post_id
        JOIN dog_profiles dp ON dp.id = ap.dog_profile_id
        SET c.branch_id = dp.branch_id
        WHERE c.type = 'ADOPTION' AND c.branch_id IS NULL;

        UPDATE conversations c
        LEFT JOIN (
            SELECT conversation_id, MAX(sent_at) AS latest_sent_at
            FROM chat_messages GROUP BY conversation_id
        ) latest ON latest.conversation_id = c.id
        SET c.last_message_at = COALESCE(latest.latest_sent_at, c.created_at)
        WHERE c.last_message_at IS NULL;

        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'conversations' AND index_name = 'uk_conversation_adoption_participants') THEN
            ALTER TABLE conversations DROP INDEX uk_conversation_adoption_participants;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'conversations' AND index_name = 'uk_conversation_adoption_customer_post') THEN
            ALTER TABLE conversations ADD CONSTRAINT uk_conversation_adoption_customer_post UNIQUE (customer_id, adoption_post_id);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'conversations' AND index_name = 'idx_conversation_customer') THEN
            ALTER TABLE conversations ADD INDEX idx_conversation_customer (customer_id);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'conversations' AND index_name = 'idx_conversation_branch_status') THEN
            ALTER TABLE conversations ADD INDEX idx_conversation_branch_status (branch_id, status);
        END IF;
    END IF;
END//

CALL pawconnect_migrate_conversation_queue()//
DROP PROCEDURE pawconnect_migrate_conversation_queue//

DELIMITER ;
