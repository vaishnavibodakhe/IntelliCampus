-- V4__add_user_specialization.sql
-- Flyway migration to add specialization column for Mentor roles

ALTER TABLE users
  ADD COLUMN specialization VARCHAR(100) NULL AFTER phone;
