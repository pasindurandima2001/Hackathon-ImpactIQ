-- ImpactIQ V3: AI Chatbot conversations and messages.
-- Target: MariaDB 10.4+ / MySQL 8, database impactiq_db. Additive only: no existing table or row is changed.
-- Prerequisite: V2__ai_investigation.sql has been applied (foreign keys reference app_users, projects,
-- project_components and defects).
-- Run once in phpMyAdmin (SQL tab, database impactiq_db) AFTER taking a backup and BEFORE starting a backend
-- build that contains the chat entities (otherwise ddl-auto=update creates weaker tables without these keys):
--   mysqldump -u <user> -p impactiq_db > impactiq_db_before_v3.sql
-- Re-running is safe: every statement uses IF NOT EXISTS.
-- Rollback: V3__rollback.sql

SET NAMES utf8mb4;

-- Conversations are private to their owner. deleted_at marks a user deletion; message content is wiped at
-- that moment and the rows are purged by the retention job.
CREATE TABLE IF NOT EXISTS chat_conversations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  component_id BIGINT NULL,
  defect_id BIGINT NULL,
  title VARCHAR(120) NOT NULL,
  message_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  deleted_at DATETIME(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_chat_conversations_owner (owner_user_id, deleted_at, updated_at),
  KEY idx_chat_conversations_project (project_id),
  KEY idx_chat_conversations_updated (updated_at),
  CONSTRAINT fk_chat_conversations_owner FOREIGN KEY (owner_user_id) REFERENCES app_users (id) ON DELETE CASCADE,
  CONSTRAINT fk_chat_conversations_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT fk_chat_conversations_component FOREIGN KEY (component_id) REFERENCES project_components (id) ON DELETE SET NULL,
  CONSTRAINT fk_chat_conversations_defect FOREIGN KEY (defect_id) REFERENCES defects (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- role: USER | ASSISTANT. status: COMPLETE | PENDING | ERROR. answer_status: GROUNDED | PARTIAL |
-- NOT_FOUND_IN_KNOWLEDGE | NEEDS_CLARIFICATION. owner_user_id is denormalised for the per-user rate limit.
CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGINT NOT NULL AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  owner_user_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  content MEDIUMTEXT NOT NULL,
  answer_status VARCHAR(32) NULL,
  citations_json MEDIUMTEXT NULL,
  details_json MEDIUMTEXT NULL,
  redaction_count INT NOT NULL DEFAULT 0,
  suspicious BIT(1) NOT NULL DEFAULT b'0',
  engine VARCHAR(16) NULL,
  model VARCHAR(64) NULL,
  duration_ms BIGINT NULL,
  error_category VARCHAR(48) NULL,
  created_at DATETIME(6) NOT NULL,
  completed_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_chat_messages_conversation (conversation_id, id),
  KEY idx_chat_messages_rate (owner_user_id, role, created_at),
  KEY idx_chat_messages_status (status, created_at),
  CONSTRAINT fk_chat_messages_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
