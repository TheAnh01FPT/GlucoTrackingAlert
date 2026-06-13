-- db_fix_healthcare_db.sql
-- BACKUP FIRST: run this locally before applying
-- mysqldump -u root -p healthcare_db > backup_healthcare_db.sql

-- This script sets all primary key and foreign key id columns to BIGINT
-- and temporarily disables foreign key checks to allow type changes.
-- Review before running.

SET FOREIGN_KEY_CHECKS=0;

-- Modify primary key columns to BIGINT (add or remove tables as needed)
ALTER TABLE roles MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE users MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE patients MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE doctors MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE doctor_patient_assignments MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE relatives MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE health_thresholds MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE password_reset_tokens MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE email_verification_tokens MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE daily_health_logs MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

-- Modify referencing foreign key columns to BIGINT
ALTER TABLE users MODIFY COLUMN role_id BIGINT;
ALTER TABLE patients MODIFY COLUMN user_id BIGINT;
ALTER TABLE doctors MODIFY COLUMN user_id BIGINT;
ALTER TABLE doctor_patient_assignments MODIFY COLUMN doctor_id BIGINT;
ALTER TABLE doctor_patient_assignments MODIFY COLUMN patient_id BIGINT;
ALTER TABLE relatives MODIFY COLUMN patient_id BIGINT;
ALTER TABLE password_reset_tokens MODIFY COLUMN user_id BIGINT;
ALTER TABLE email_verification_tokens MODIFY COLUMN user_id BIGINT;
ALTER TABLE health_thresholds MODIFY COLUMN patient_id BIGINT;
ALTER TABLE health_thresholds MODIFY COLUMN updated_by BIGINT;
ALTER TABLE daily_health_logs MODIFY COLUMN patient_id BIGINT;

SET FOREIGN_KEY_CHECKS=1;

-- After running, verify foreign keys exist and are correct.
-- If any ALTER fails due to missing tables/columns, edit this file to match your schema.
