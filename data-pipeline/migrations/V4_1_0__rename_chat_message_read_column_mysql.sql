-- PawConnect Chat MySQL compatibility migration.
-- Run once on the target `pawconnect` schema after a backup and before setting JPA_DDL_AUTO=validate.
-- It preserves existing message values and does nothing when the schema is already compatible.

DELIMITER //

CREATE PROCEDURE pawconnect_rename_chat_message_read_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'chat_messages'
    ) THEN
        CREATE TABLE chat_messages (
            id BIGINT NOT NULL AUTO_INCREMENT,
            conversation_id BIGINT NOT NULL,
            sender_id BIGINT NOT NULL,
            content VARCHAR(4000) NOT NULL,
            sent_at DATETIME(6) NOT NULL,
            is_read BOOLEAN NOT NULL DEFAULT FALSE,
            PRIMARY KEY (id),
            CONSTRAINT fk_chat_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id),
            CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id),
            INDEX idx_chat_conversation_time (conversation_id, sent_at)
        );
    ELSE
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'chat_messages'
              AND column_name = 'read'
        ) AND NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'chat_messages'
              AND column_name = 'is_read'
        ) THEN
            ALTER TABLE chat_messages CHANGE COLUMN `read` is_read BOOLEAN NOT NULL DEFAULT FALSE;
        ELSEIF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'chat_messages'
              AND column_name = 'is_read'
        ) THEN
            ALTER TABLE chat_messages ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE;
        END IF;
    END IF;
END//

CALL pawconnect_rename_chat_message_read_column()//
DROP PROCEDURE pawconnect_rename_chat_message_read_column//

DELIMITER ;
