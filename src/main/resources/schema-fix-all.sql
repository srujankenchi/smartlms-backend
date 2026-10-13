-- =====================================================================
-- SmartLMS — Unified MySQL Schema Fix (users, courses, lessons)
-- =====================================================================
-- Fixes "Field 'active' doesn't have a default value" across all tables.
-- Non-destructive: preserves existing tables, data, and constraints.
-- =====================================================================

USE smartlms;

-- 1. Fix users table legacy columns
ALTER TABLE users 
    MODIFY COLUMN full_name VARCHAR(255) NULL DEFAULT NULL,
    MODIFY COLUMN active BIT(1) NULL DEFAULT b'1';

UPDATE users 
   SET active = b'1' 
 WHERE active IS NULL;

-- 2. Fix courses table active and code columns
ALTER TABLE courses 
    MODIFY COLUMN active BIT(1) NOT NULL DEFAULT b'1';

UPDATE courses 
   SET active = b'1' 
 WHERE active IS NULL;

-- Populate codes for any legacy rows missing a code
UPDATE courses SET code = 'C-PROG' WHERE (code IS NULL OR code = '') AND title LIKE '%C Programming%';
UPDATE courses SET code = 'JAVA-PROG' WHERE (code IS NULL OR code = '') AND title LIKE '%Java Programming%';
UPDATE courses SET code = 'PYTHON-PROG' WHERE (code IS NULL OR code = '') AND title LIKE '%Python Programming%';
UPDATE courses SET code = 'HTML-CSS' WHERE (code IS NULL OR code = '') AND title LIKE '%HTML%';
UPDATE courses SET code = 'DBMS' WHERE (code IS NULL OR code = '') AND title LIKE '%DBMS%';
UPDATE courses SET code = 'REACT' WHERE (code IS NULL OR code = '') AND title LIKE '%React%';

-- 3. Fix lessons table active column (if present)
-- ALTER TABLE lessons MODIFY COLUMN active BIT(1) NOT NULL DEFAULT b'1';
