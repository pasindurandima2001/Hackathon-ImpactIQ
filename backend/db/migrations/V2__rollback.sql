-- Rollback for V2__ai_investigation.sql. Deletes ALL investigation, evidence, knowledge, project, user and audit data.
-- Take a backup first. Original defects and impact analyses are kept; only the three added defect columns are dropped.
-- Stop the backend before running, otherwise ddl-auto=update recreates the tables on next start.

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS investigation_feedback;
DROP TABLE IF EXISTS investigation_steps;
DROP TABLE IF EXISTS investigations;
DROP TABLE IF EXISTS defect_evidence;
DROP TABLE IF EXISTS document_chunks;
DROP TABLE IF EXISTS document_versions;
DROP TABLE IF EXISTS knowledge_documents;
DROP TABLE IF EXISTS project_members;
DROP TABLE IF EXISTS project_components;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS audit_events;
DROP TABLE IF EXISTS app_users;
SET FOREIGN_KEY_CHECKS = 1;

ALTER TABLE defects DROP INDEX IF EXISTS idx_defects_project;
ALTER TABLE defects
  DROP COLUMN IF EXISTS project_id,
  DROP COLUMN IF EXISTS component_id,
  DROP COLUMN IF EXISTS created_by;
