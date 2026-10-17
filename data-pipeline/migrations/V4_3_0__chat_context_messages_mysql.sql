-- Adds structured listing/adoption preview data to chat messages. Run once after V4_2_0.
DELIMITER //
CREATE PROCEDURE pawconnect_add_chat_context_messages()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'chat_messages' AND column_name = 'message_type') THEN
        ALTER TABLE chat_messages ADD COLUMN message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'chat_messages' AND column_name = 'context_url') THEN
        ALTER TABLE chat_messages ADD COLUMN context_url VARCHAR(1000) NULL;
        ALTER TABLE chat_messages ADD COLUMN context_title VARCHAR(255) NULL;
        ALTER TABLE chat_messages ADD COLUMN context_summary VARCHAR(1000) NULL;
        ALTER TABLE chat_messages ADD COLUMN context_image_url VARCHAR(1000) NULL;
    END IF;
END//
CALL pawconnect_add_chat_context_messages()//
DROP PROCEDURE pawconnect_add_chat_context_messages//
DELIMITER ;
