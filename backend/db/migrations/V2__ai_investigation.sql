-- ImpactIQ V2: authentication, projects, project knowledge (RAG), defect evidence and multi-agent investigations.
-- Target: MariaDB 10.4+ / MySQL 8, database impactiq_db. Additive only: existing tables and rows are preserved.
-- Run once in phpMyAdmin (SQL tab, database impactiq_db) AFTER taking a backup:
--   mysqldump -u <user> -p impactiq_db > impactiq_db_before_v2.sql
-- Re-running is safe: every statement uses IF NOT EXISTS.
-- Rollback: V2__rollback.sql

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS app_users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(120) NULL,
  role VARCHAR(16) NOT NULL,
  enabled BIT(1) NOT NULL DEFAULT b'1',
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS projects (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NULL,
  created_by BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_projects_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_components (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_components_name (project_id, name),
  CONSTRAINT fk_project_components_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_members (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_members_user (project_id, user_id),
  KEY idx_project_members_user (user_id),
  CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing defects stay unscoped (project_id NULL) and remain visible to every signed-in user.
ALTER TABLE defects
  ADD COLUMN IF NOT EXISTS project_id BIGINT NULL,
  ADD COLUMN IF NOT EXISTS component_id BIGINT NULL,
  ADD COLUMN IF NOT EXISTS created_by BIGINT NULL;
ALTER TABLE defects ADD INDEX IF NOT EXISTS idx_defects_project (project_id, component_id);

CREATE TABLE IF NOT EXISTS knowledge_documents (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  component_id BIGINT NULL,
  title VARCHAR(255) NOT NULL,
  source_type VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  active_version_number INT NULL,
  latest_version_number INT NOT NULL,
  last_error VARCHAR(1000) NULL,
  uploaded_by BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_knowledge_documents_project (project_id),
  CONSTRAINT fk_knowledge_documents_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT fk_knowledge_documents_component FOREIGN KEY (component_id) REFERENCES project_components (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS document_versions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  version_number INT NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  stored_filename VARCHAR(80) NOT NULL,
  content_type VARCHAR(100) NULL,
  size_bytes BIGINT NOT NULL,
  sha256 VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  error_message VARCHAR(1000) NULL,
  chunk_count INT NULL,
  page_count INT NULL,
  embedding_status VARCHAR(16) NULL,
  redaction_count INT NOT NULL DEFAULT 0,
  file_deleted BIT(1) NOT NULL DEFAULT b'0',
  uploaded_by BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  processed_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_document_versions_number (document_id, version_number),
  CONSTRAINT fk_document_versions_document FOREIGN KEY (document_id) REFERENCES knowledge_documents (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Retrieval always filters by project_id first (see idx_document_chunks_project); embeddings are float32 BLOBs.
CREATE TABLE IF NOT EXISTS document_chunks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  version_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  component_id BIGINT NULL,
  chunk_index INT NOT NULL,
  content MEDIUMTEXT NOT NULL,
  start_line INT NULL,
  end_line INT NULL,
  page_number INT NULL,
  embedding MEDIUMBLOB NULL,
  embedding_model VARCHAR(64) NULL,
  suspicious BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  KEY idx_document_chunks_project (project_id, component_id),
  KEY idx_document_chunks_version (version_id),
  CONSTRAINT fk_document_chunks_document FOREIGN KEY (document_id) REFERENCES knowledge_documents (id) ON DELETE CASCADE,
  CONSTRAINT fk_document_chunks_version FOREIGN KEY (version_id) REFERENCES document_versions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS defect_evidence (
  id BIGINT NOT NULL AUTO_INCREMENT,
  defect_id BIGINT NOT NULL,
  type VARCHAR(24) NOT NULL,
  label VARCHAR(255) NOT NULL,
  content MEDIUMTEXT NOT NULL,
  source_filename VARCHAR(255) NULL,
  content_sha256 VARCHAR(64) NOT NULL,
  redaction_count INT NOT NULL DEFAULT 0,
  suspicious BIT(1) NOT NULL DEFAULT b'0',
  created_by BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_defect_evidence_defect (defect_id),
  CONSTRAINT fk_defect_evidence_defect FOREIGN KEY (defect_id) REFERENCES defects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS investigations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  defect_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  component_id BIGINT NULL,
  status VARCHAR(16) NOT NULL,
  attempt INT NOT NULL,
  parent_investigation_id BIGINT NULL,
  triggered_by BIGINT NULL,
  engine VARCHAR(16) NULL,
  model VARCHAR(64) NULL,
  baseline_score INT NOT NULL,
  baseline_risk_level VARCHAR(16) NULL,
  baseline_priority VARCHAR(4) NULL,
  report_json LONGTEXT NULL,
  error_category VARCHAR(48) NULL,
  error_message VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL,
  started_at DATETIME(6) NULL,
  completed_at DATETIME(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_investigations_defect (defect_id, created_at),
  KEY idx_investigations_status (status),
  CONSTRAINT fk_investigations_defect FOREIGN KEY (defect_id) REFERENCES defects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS investigation_steps (
  id BIGINT NOT NULL AUTO_INCREMENT,
  investigation_id BIGINT NOT NULL,
  agent_name VARCHAR(48) NOT NULL,
  sequence_number INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  duration_ms BIGINT NULL,
  error_category VARCHAR(48) NULL,
  error_message VARCHAR(500) NULL,
  summary VARCHAR(500) NULL,
  output_json MEDIUMTEXT NULL,
  started_at DATETIME(6) NULL,
  completed_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_investigation_steps_run (investigation_id),
  CONSTRAINT fk_investigation_steps_run FOREIGN KEY (investigation_id) REFERENCES investigations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS investigation_feedback (
  id BIGINT NOT NULL AUTO_INCREMENT,
  investigation_id BIGINT NOT NULL,
  hypothesis_rank INT NULL,
  verdict VARCHAR(16) NOT NULL,
  comment VARCHAR(2000) NULL,
  user_id BIGINT NULL,
  username VARCHAR(64) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_investigation_feedback_run (investigation_id),
  CONSTRAINT fk_investigation_feedback_run FOREIGN KEY (investigation_id) REFERENCES investigations (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Audit rows intentionally have no foreign keys so history survives deletions (pruned by retention).
CREATE TABLE IF NOT EXISTS audit_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  actor_user_id BIGINT NULL,
  actor_username VARCHAR(64) NULL,
  action VARCHAR(64) NOT NULL,
  entity_type VARCHAR(64) NULL,
  entity_id BIGINT NULL,
  project_id BIGINT NULL,
  detail VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_audit_events_project (project_id, created_at),
  KEY idx_audit_events_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
