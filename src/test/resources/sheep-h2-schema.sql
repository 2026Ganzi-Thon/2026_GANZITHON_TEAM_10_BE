-- H2 equivalent of db/sheep-mysql.sql; H2 has case-sensitive VARCHAR by default.
-- New MySQL 8 schema: Sheep only. Apply manually to a new local schema first.
-- DATETIME stores Asia/Seoul local times. User/Record tables are owned by other domains.
CREATE TABLE sheep_wallets (
 user_id BIGINT NOT NULL,
 balance INT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(user_id),
 CONSTRAINT chk_sheep_wallet_balance CHECK(balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sheep_transactions (
 id BIGINT NOT NULL AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 transaction_type VARCHAR(32) NOT NULL,
 reference_id VARCHAR(160) NOT NULL,
 meeting_id BIGINT NULL,
 amount INT NOT NULL,
 balance_after INT NOT NULL,
 reward_mode VARCHAR(8) NOT NULL,
 reward_date DATE NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(id),
 CONSTRAINT fk_sheep_transaction_wallet FOREIGN KEY(user_id) REFERENCES sheep_wallets(user_id) ON DELETE RESTRICT,
 CONSTRAINT uk_sheep_source UNIQUE(user_id,transaction_type,reference_id),
 CONSTRAINT uk_sheep_meeting UNIQUE(user_id,meeting_id,transaction_type),
 CONSTRAINT uk_sheep_daily_reward UNIQUE(user_id,transaction_type,reward_date),
 CONSTRAINT chk_sheep_balance CHECK(balance_after >= 0),
 CONSTRAINT chk_sheep_amount_mode CHECK(
  (transaction_type='RECORD' AND amount=1 AND ((reward_mode='DAILY' AND reward_date IS NOT NULL) OR (reward_mode='DEMO' AND reward_date IS NULL)))
  OR (transaction_type='MEETING_REFUND' AND amount=1 AND reward_mode='NONE' AND reward_date IS NULL)
  OR (transaction_type IN ('MEETING_CREATE','MEETING_JOIN','AI_EXTRA_CHAT') AND amount=-1 AND reward_mode='NONE' AND reward_date IS NULL)),
 CONSTRAINT chk_sheep_meeting_id CHECK(
  (transaction_type IN ('MEETING_CREATE','MEETING_JOIN','MEETING_REFUND') AND meeting_id IS NOT NULL)
  OR (transaction_type IN ('RECORD','AI_EXTRA_CHAT') AND meeting_id IS NULL)),
 INDEX idx_sheep_user_id(user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sheep_record_rewards (
 record_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 decided_date DATE NOT NULL,
 sheep_earned INT NOT NULL,
 transaction_id BIGINT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(record_id),
 CONSTRAINT fk_sheep_decision_wallet FOREIGN KEY(user_id) REFERENCES sheep_wallets(user_id) ON DELETE RESTRICT,
 CONSTRAINT fk_sheep_decision_transaction FOREIGN KEY(transaction_id) REFERENCES sheep_transactions(id) ON DELETE RESTRICT,
 CONSTRAINT uk_sheep_decision_transaction UNIQUE(transaction_id),
 CONSTRAINT chk_sheep_decision CHECK((sheep_earned=0 AND transaction_id IS NULL) OR (sheep_earned=1 AND transaction_id IS NOT NULL)),
 INDEX idx_sheep_record_user_date(user_id,decided_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
