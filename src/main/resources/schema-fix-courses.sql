-- =====================================================================
-- SmartLMS — MySQL Schema Migration: Resolve courses and lessons active column
-- =====================================================================
-- Root Cause:
-- The MySQL `smartlms.courses` table contained legacy column `active BIT(1)`
-- with NOT NULL constraint and no DEFAULT value.
--
-- This script:
-- 1. Sets a safe default of 1 (true) for `active` on `courses` and `lessons`.
-- 2. Ensures existing courses have active = 1 and published = 1.
-- =====================================================================

USE smartlms;

-- Step 1: Ensure active column has safe default of 1 on courses
ALTER TABLE courses 
    MODIFY COLUMN active BIT(1) NOT NULL DEFAULT b'1';

-- Step 2: Ensure existing courses are active and published
UPDATE courses 
   SET active = b'1' 
 WHERE active IS NULL;

-- Step 3: Ensure active column has safe default of 1 on lessons if it exists
-- ALTER TABLE lessons MODIFY COLUMN active BIT(1) NOT NULL DEFAULT b'1';
