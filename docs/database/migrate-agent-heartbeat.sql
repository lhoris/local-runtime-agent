-- Add Agent-level liveness fields without deleting existing rows.
-- Run this against an existing MariaDB database after taking a backup.

ALTER TABLE TB_M26_AGENT
    ADD COLUMN IF NOT EXISTS WORKED_AT TIMESTAMP NULL
            COMMENT 'Timestamp of the latest Agent loop execution';

ALTER TABLE TB_M26_AGENT
    MODIFY COLUMN WORKED_AT TIMESTAMP NULL
        COMMENT 'Timestamp of the latest Agent loop execution';

SELECT AGENT_ID,
       IP_ADDRESS,
       WORKED_AT,
       TIMESTAMPDIFF(SECOND, WORKED_AT, CURRENT_TIMESTAMP()) AS WORKED_AGE_SEC
FROM TB_M26_AGENT
ORDER BY AGENT_ID;
