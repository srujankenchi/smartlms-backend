-- =====================================================================
-- SmartLMS — Production Database Migration & Safe Table Cleanup
-- Target Database: smartlms
-- Backup verified: %USERPROFILE%\Desktop\smartlms-backup.sql (118,540 bytes)
-- =====================================================================
-- EXACT SOURCE SCHEMA (`assignment_submissions`):
--   id, assignment_id, student_id, content, feedback, file_url,
--   graded_at, marks_obtained, status, submission_date, file_name,
--   file_size, file_type
--   (NOTE: Does NOT contain original_file_name, remarks, or submitted_at)
--
-- TARGET SCHEMA (`submissions`):
--   id, assignment_id, student_id, content, feedback, file_url,
--   graded_at, marks_obtained, status, submitted_at, file_name,
--   file_size, file_type, original_file_name, remarks
-- =====================================================================
-- SCRIPT STRUCTURE:
--   PHASE 1: Schema extension on target `submissions` (with DATETIME(6))
--   PHASE 2: Pre-migration conflict and duplicate validation
--   PHASE 3: Safe, resumable data migration (inserts only missing rows)
--   PHASE 4: Automated data validation gate (with detailed diagnostics)
--   PHASE 5: Unique constraint addition (only after 0 duplicates verified)
--   PHASE 6: [DISABLED / SAFEGUARD] Table drop section (requires approval)
-- =====================================================================

USE smartlms;

-- =====================================================================
-- PHASE 1: EXTEND `submissions` TABLE SCHEMA
-- Add missing columns to mirror the legacy schema without data loss.
-- Uses DATETIME(6) to preserve microsecond precision.
-- =====================================================================

-- 1. status
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'status');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE submissions ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT \'SUBMITTED\'', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. marks_obtained (DOUBLE to preserve exact MySQL DOUBLE precision)
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'marks_obtained');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE submissions ADD COLUMN marks_obtained DOUBLE NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. feedback
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'feedback');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE submissions ADD COLUMN feedback TEXT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. graded_at (Ensure DATETIME(6) with microsecond precision)
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'graded_at');
SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE submissions ADD COLUMN graded_at DATETIME(6) NULL', 
    'ALTER TABLE submissions MODIFY COLUMN graded_at DATETIME(6) NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. submitted_at (Ensure DATETIME(6) to match legacy submission_date)
ALTER TABLE submissions MODIFY COLUMN submitted_at DATETIME(6) NULL;

-- 6. content
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'content');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE submissions ADD COLUMN content LONGTEXT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7. file_size
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'file_size');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE submissions ADD COLUMN file_size BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 8. file_type
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'file_type');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE submissions ADD COLUMN file_type VARCHAR(100) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 9. assignments table: ensure due_date is nullable if created by Hibernate ddl-auto
SET @has_due_date = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'assignments' AND COLUMN_NAME = 'due_date');
SET @sql_dd = IF(@has_due_date > 0, 'ALTER TABLE assignments MODIFY COLUMN due_date DATETIME NULL', 'SELECT 1');
PREPARE stmt FROM @sql_dd; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- =====================================================================
-- PHASE 2: PRE-MIGRATION CONFLICT & COLLISION CHECKS
-- Abort before touching any data if any conflict exists.
-- =====================================================================

DROP PROCEDURE IF EXISTS check_pre_migration_conflicts;
DELIMITER //
CREATE PROCEDURE check_pre_migration_conflicts()
BEGIN
    DECLARE pk_collision_cnt INT DEFAULT 0;
    DECLARE pair_collision_cnt INT DEFAULT 0;
    DECLARE duplicate_pairs_cnt INT DEFAULT 0;
    DECLARE data_conflict_cnt INT DEFAULT 0;

    -- 1. Check if same ID exists in submissions but belongs to different assignment or student
    SELECT COUNT(*) INTO pk_collision_cnt
    FROM assignment_submissions a
    INNER JOIN submissions s ON a.id = s.id
    WHERE a.assignment_id <> s.assignment_id OR a.student_id <> s.student_id;

    IF pk_collision_cnt > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ABORT: Primary key collision detected! Same ID exists in submissions with different assignment/student.';
    END IF;

    -- 2. Check if same (assignment_id, student_id) exists in submissions under a DIFFERENT ID
    SELECT COUNT(*) INTO pair_collision_cnt
    FROM assignment_submissions a
    INNER JOIN submissions s ON a.assignment_id = s.assignment_id AND a.student_id = s.student_id
    WHERE a.id <> s.id;

    IF pair_collision_cnt > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ABORT: Unique key conflict! Same (assignment_id, student_id) already exists in submissions under a different ID.';
    END IF;

    -- 3. Check if assignment_submissions itself has duplicate (assignment_id, student_id) pairs
    SELECT COUNT(*) INTO duplicate_pairs_cnt
    FROM (
        SELECT assignment_id, student_id
        FROM assignment_submissions
        GROUP BY assignment_id, student_id
        HAVING COUNT(*) > 1
    ) AS dup_pairs;

    IF duplicate_pairs_cnt > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ABORT: Source table assignment_submissions contains duplicate (assignment_id, student_id) pairs.';
    END IF;

    -- 4. Check if an existing row in submissions already has different/conflicting data
    SELECT COUNT(*) INTO data_conflict_cnt
    FROM assignment_submissions a
    INNER JOIN submissions s ON a.id = s.id
    WHERE a.assignment_id <> s.assignment_id
       OR a.student_id <> s.student_id
       OR NOT (a.status <=> s.status)
       OR NOT (a.marks_obtained <=> s.marks_obtained)
       OR NOT (a.feedback <=> s.feedback)
       OR NOT (a.file_url <=> s.file_url)
       OR NOT (a.file_name <=> s.file_name)
       OR NOT (a.file_size <=> s.file_size)
       OR NOT (a.file_type <=> s.file_type)
       OR NOT (a.content <=> s.content)
       OR NOT (a.submission_date <=> s.submitted_at)
       OR NOT (a.graded_at <=> s.graded_at);

    IF data_conflict_cnt > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ABORT: Data conflict! An existing row in submissions differs from assignment_submissions. Aborting to prevent silent overwrite.';
    END IF;

    SELECT 'Pre-migration checks passed: 0 collisions, 0 conflicts, 0 duplicate pairs.' AS pre_check_status;
END //
DELIMITER ;

CALL check_pre_migration_conflicts();
DROP PROCEDURE check_pre_migration_conflicts;


-- =====================================================================
-- PHASE 3: SAFE, RESUMABLE DATA MIGRATION
-- Inserts only missing rows, preserving actual IDs, actual student IDs
-- (IDs 5 and 6), actual assignment IDs, marks, feedback, status, content,
-- file metadata, and maps submission_date -> submitted_at.
-- Target fields original_file_name and remarks are set to NULL.
-- Safely idempotent: if rows already exist (resumed run), 0 rows inserted.
-- =====================================================================

INSERT INTO submissions (
    id,
    assignment_id,
    student_id,
    content,
    feedback,
    file_url,
    file_name,
    file_size,
    file_type,
    original_file_name,
    remarks,
    status,
    marks_obtained,
    graded_at,
    submitted_at
)
SELECT 
    a.id,
    a.assignment_id,
    a.student_id,
    a.content,
    a.feedback,
    a.file_url,
    a.file_name,
    a.file_size,
    a.file_type,
    NULL,                 -- original_file_name does not exist in legacy schema (NULL)
    NULL,                 -- remarks does not exist in legacy schema (NULL)
    a.status,
    a.marks_obtained,
    a.graded_at,
    a.submission_date     -- legacy submission_date maps directly to submitted_at
FROM assignment_submissions a
WHERE NOT EXISTS (
    SELECT 1 FROM submissions s WHERE s.id = a.id
);

-- Reset AUTO_INCREMENT on `submissions` to MAX(id) + 1 to avoid future ID clashes
SET @max_id = (SELECT IFNULL(MAX(id), 0) + 1 FROM submissions);
SET @sql_ai = CONCAT('ALTER TABLE submissions AUTO_INCREMENT = ', @max_id);
PREPARE stmt FROM @sql_ai; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- =====================================================================
-- PHASE 4: AUTOMATED DATA FIDELITY & REFERENTIAL VALIDATION GATE
-- Uses hard SIGNAL SQLSTATE errors to halt if ANY mismatch occurs.
-- Emits full diagnostics on mismatch before halting.
-- =====================================================================

DROP PROCEDURE IF EXISTS validate_migration_gate;
DELIMITER //
CREATE PROCEDURE validate_migration_gate()
BEGIN
    DECLARE legacy_cnt INT DEFAULT 0;
    DECLARE active_cnt INT DEFAULT 0;
    DECLARE mismatch_cnt INT DEFAULT 0;
    DECLARE orphan_cnt INT DEFAULT 0;

    SELECT COUNT(*) INTO legacy_cnt FROM assignment_submissions;
    SELECT COUNT(*) INTO active_cnt FROM submissions;

    -- Gate 1: Row count check (target must contain at least as many rows as legacy)
    IF active_cnt < legacy_cnt THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'GATE FAILED: Target submissions count is less than legacy assignment_submissions count!';
    END IF;

    -- Gate 2: Value-by-value data fidelity comparison for every single legacy record
    SELECT COUNT(*) INTO mismatch_cnt
    FROM assignment_submissions a
    LEFT JOIN submissions s ON a.id = s.id
    WHERE s.id IS NULL
       OR a.assignment_id <> s.assignment_id
       OR a.student_id <> s.student_id
       OR NOT (a.status <=> s.status)
       OR NOT (a.marks_obtained <=> s.marks_obtained)
       OR NOT (a.feedback <=> s.feedback)
       OR NOT (a.file_url <=> s.file_url)
       OR NOT (a.file_name <=> s.file_name)
       OR NOT (a.file_size <=> s.file_size)
       OR NOT (a.file_type <=> s.file_type)
       OR NOT (a.content <=> s.content)
       OR NOT (a.submission_date <=> s.submitted_at)
       OR NOT (a.graded_at <=> s.graded_at);

    IF mismatch_cnt > 0 THEN
        -- Emit diagnostics table showing exact columns with mismatch
        SELECT 
            a.id AS record_id,
            IF(s.id IS NOT NULL, 'PRESENT', 'MISSING') AS target_record,
            IF(a.assignment_id <=> s.assignment_id, 'OK', 'MISMATCH') AS assignment_id_chk,
            IF(a.student_id <=> s.student_id, 'OK', 'MISMATCH') AS student_id_chk,
            IF(a.status <=> s.status, 'OK', 'MISMATCH') AS status_chk,
            IF(a.marks_obtained <=> s.marks_obtained, 'OK', 'MISMATCH') AS marks_chk,
            IF(a.feedback <=> s.feedback, 'OK', 'MISMATCH') AS feedback_chk,
            IF(a.file_url <=> s.file_url, 'OK', 'MISMATCH') AS file_url_chk,
            IF(a.file_name <=> s.file_name, 'OK', 'MISMATCH') AS file_name_chk,
            IF(a.file_size <=> s.file_size, 'OK', 'MISMATCH') AS file_size_chk,
            IF(a.file_type <=> s.file_type, 'OK', 'MISMATCH') AS file_type_chk,
            IF(a.content <=> s.content, 'OK', 'MISMATCH') AS content_chk,
            IF(a.submission_date <=> s.submitted_at, 'OK', 'MISMATCH') AS submission_date_chk,
            IF(a.graded_at <=> s.graded_at, 'OK', 'MISMATCH') AS graded_at_chk
        FROM assignment_submissions a
        LEFT JOIN submissions s ON a.id = s.id;

        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'GATE FAILED: Data fidelity check failed! Inspect diagnostic output for mismatched columns.';
    END IF;

    -- Gate 3: Referential integrity check (student_id and assignment_id must exist in parents)
    SELECT COUNT(*) INTO orphan_cnt
    FROM submissions s
    LEFT JOIN assignments a ON s.assignment_id = a.id
    LEFT JOIN users u ON s.student_id = u.id
    WHERE a.id IS NULL OR u.id IS NULL;

    IF orphan_cnt > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'GATE FAILED: Referential integrity check failed! Submissions table contains orphaned foreign keys.';
    END IF;

    SELECT 'VALIDATION PASSED: All records, fields, microsecond timestamps, and foreign keys verified successfully!' AS gate_status;
END //
DELIMITER ;

CALL validate_migration_gate();
DROP PROCEDURE validate_migration_gate;


-- =====================================================================
-- PHASE 5: ADD UNIQUE CONSTRAINT ON (assignment_id, student_id)
-- Safely applied only after verifying zero duplicates exist in submissions.
-- =====================================================================

SET @dup_submissions_cnt = (
    SELECT COUNT(*) 
    FROM (
        SELECT assignment_id, student_id 
        FROM submissions 
        GROUP BY assignment_id, student_id 
        HAVING COUNT(*) > 1
    ) AS dups
);

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND CONSTRAINT_NAME = 'uk_submission_assignment_student');

SET @sql_uk = IF(@dup_submissions_cnt = 0 AND @idx_exists = 0,
    'ALTER TABLE submissions ADD CONSTRAINT uk_submission_assignment_student UNIQUE (assignment_id, student_id)',
    'SELECT \'Skipped adding unique constraint: duplicates found or constraint already exists\' AS notice'
);
PREPARE stmt FROM @sql_uk; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- =====================================================================
-- VERIFICATION REPORT QUERIES
-- Display migrated rows for final inspection
-- =====================================================================

SELECT 
    s.id,
    s.assignment_id,
    s.student_id,
    s.status,
    s.marks_obtained,
    s.file_name,
    s.file_size,
    s.file_type,
    s.submitted_at,
    s.graded_at,
    LEFT(s.feedback, 50) AS feedback_preview,
    LEFT(s.content, 50) AS content_preview
FROM submissions s;


-- =====================================================================
-- PHASE 6: SAFELY REMOVE CONFIRMED UNUSED TABLES (18 TABLES)
-- [SAFEGUARD: DISABLED AND ENCLOSED. DO NOT RUN UNTIL EXPLICITLY APPROVED]
-- =====================================================================
/*
SET FOREIGN_KEY_CHECKS = 0;

-- 1. Quizzes subsystem (5 tables)
DROP TABLE IF EXISTS quiz_answers;
DROP TABLE IF EXISTS quiz_options;
DROP TABLE IF EXISTS quiz_questions;
DROP TABLE IF EXISTS quiz_attempts;
DROP TABLE IF EXISTS quizzes;

-- 2. Attendance subsystem (1 table)
DROP TABLE IF EXISTS attendance;

-- 3. Grading & Lesson Progress subsystems (2 tables)
DROP TABLE IF EXISTS grades;
DROP TABLE IF EXISTS lesson_progress;

-- 4. Enrollments subsystem (1 table)
DROP TABLE IF EXISTS enrollments;

-- 5. Course modules subsystem (1 table)
DROP TABLE IF EXISTS modules;

-- 6. Notices & Notifications subsystems (2 tables)
DROP TABLE IF EXISTS notices;
DROP TABLE IF EXISTS notifications;

-- 7. Resources & Schedules subsystems (2 tables)
DROP TABLE IF EXISTS resources;
DROP TABLE IF EXISTS schedules;

-- 8. Categories & Departments subsystems (2 tables)
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS departments;

-- 9. Redundant legacy submissions table (1 table)
DROP TABLE IF EXISTS assignment_submissions;

-- 10. Legacy metadata tracker (1 table)
DROP TABLE IF EXISTS system_meta;

SET FOREIGN_KEY_CHECKS = 1;

SHOW TABLES;
*/
