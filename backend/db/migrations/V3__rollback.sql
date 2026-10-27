-- Rollback for V3__chatbot.sql. Deletes ALL chatbot conversations and messages; nothing else is touched.
-- Take a backup first. Stop the backend before running, otherwise ddl-auto=update recreates the tables on next start.

DROP TABLE IF EXISTS chat_messages;
DROP TABLE IF EXISTS chat_conversations;
