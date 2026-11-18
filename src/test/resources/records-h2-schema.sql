-- MySQL8 initial Records schema. Apply after sheep-mysql.sql to a new local database.
-- No automatic execution. No User/Drawing foreign keys until integration is reviewed.
CREATE TABLE records (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL,
 title VARCHAR(500) NULL,
 image_url VARCHAR(2048) NULL,
 drawing_description TEXT NULL,
 main_elements TEXT NULL,
 spatial_composition TEXT NULL,
 line_expression TEXT NULL,
 emotion_meaning TEXT NULL,
 created_at DATETIME(6) NOT NULL,
 record_date DATE NULL,
 completed_at DATETIME(6) NULL,
 deleted_at DATETIME(6) NULL,
 CONSTRAINT chk_record_status CHECK(status IN ('IN_PROGRESS','CARD_READY','COMPLETED','DELETED')),
 INDEX idx_records_owner_completion(user_id,status,completed_at,id),
 INDEX idx_records_owner_date(user_id,status,record_date,completed_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE record_messages (
 record_id BIGINT NOT NULL,
 message_index INT NOT NULL,
 role VARCHAR(20) NOT NULL,
 content TEXT NOT NULL,
 sent_at DATETIME(6) NOT NULL,
 PRIMARY KEY(record_id,message_index),
 CONSTRAINT fk_record_message_record FOREIGN KEY(record_id) REFERENCES records(id) ON DELETE RESTRICT,
 CONSTRAINT chk_record_message_role CHECK(role IN ('USER','ASSISTANT')),
 CONSTRAINT chk_record_message_index CHECK(message_index>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
