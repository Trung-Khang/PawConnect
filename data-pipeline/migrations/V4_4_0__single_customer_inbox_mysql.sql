-- One customer owns one durable inbox. Existing duplicate conversations are merged
-- into the oldest conversation so no chat history is discarded.
DROP TEMPORARY TABLE IF EXISTS pawconnect_conversation_keepers;
CREATE TEMPORARY TABLE pawconnect_conversation_keepers AS
SELECT customer_id, MIN(id) AS keeper_id
FROM conversations
GROUP BY customer_id
HAVING COUNT(*) > 1;

UPDATE chat_messages message
JOIN conversations duplicate_conversation ON duplicate_conversation.id = message.conversation_id
JOIN pawconnect_conversation_keepers keeper ON keeper.customer_id = duplicate_conversation.customer_id
SET message.conversation_id = keeper.keeper_id
WHERE duplicate_conversation.id <> keeper.keeper_id;

DELETE duplicate_conversation
FROM conversations duplicate_conversation
JOIN pawconnect_conversation_keepers keeper ON keeper.customer_id = duplicate_conversation.customer_id
WHERE duplicate_conversation.id <> keeper.keeper_id;

DROP TEMPORARY TABLE pawconnect_conversation_keepers;

DELIMITER //
CREATE PROCEDURE pawconnect_single_customer_inbox()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'conversations'
          AND index_name = 'uk_conversation_adoption_customer_post'
    ) THEN
        ALTER TABLE conversations DROP INDEX uk_conversation_adoption_customer_post;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'conversations'
          AND index_name = 'uk_conversation_customer'
    ) THEN
        ALTER TABLE conversations ADD CONSTRAINT uk_conversation_customer UNIQUE (customer_id);
    END IF;
END//
CALL pawconnect_single_customer_inbox()//
DROP PROCEDURE pawconnect_single_customer_inbox//
DELIMITER ;
