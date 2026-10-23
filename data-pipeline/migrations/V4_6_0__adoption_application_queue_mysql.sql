-- Keep a separate processing timestamp so completed applications can be ordered by their latest outcome.
-- The guarded procedure makes this safe to re-run against a partially migrated database.
DELIMITER //

CREATE PROCEDURE pawconnect_add_adoption_application_updated_at()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'adoption_applications'
          AND column_name = 'updated_at'
    ) THEN
        ALTER TABLE adoption_applications
            ADD COLUMN updated_at TIMESTAMP(6) NULL AFTER created_at;
    END IF;

    UPDATE adoption_applications
    SET updated_at = created_at
    WHERE updated_at IS NULL;

    ALTER TABLE adoption_applications
        MODIFY COLUMN updated_at TIMESTAMP(6) NOT NULL;
END//

CALL pawconnect_add_adoption_application_updated_at()//
DROP PROCEDURE pawconnect_add_adoption_application_updated_at//

DELIMITER ;
