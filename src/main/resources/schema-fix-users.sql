-- =====================================================================
-- SmartLMS — MySQL Schema Migration: Resolve users table column mismatch
-- =====================================================================
-- Root Cause:
-- The MySQL `smartlms.users` table contained legacy columns `full_name` and
-- `active` with NOT NULL constraints and no DEFAULT values. The JPA entity
-- `User.java` and frontend exclusively use `name` and `enabled`.
--
-- This script:
-- 1. Safely syncs data from legacy columns to canonical columns (zero data loss).
-- 2. Relaxes NOT NULL constraints on legacy columns so JPA INSERT statements succeed.
-- 3. Optionally drops the legacy columns if schema cleanup is desired.
-- =====================================================================

USE smartlms;

-- Step 1: Preserve existing data (backfill canonical columns if empty)
UPDATE users 
   SET name = full_name 
 WHERE (name IS NULL OR name = '') 
   AND full_name IS NOT NULL;

UPDATE users 
   SET enabled = active 
 WHERE enabled IS NULL 
   AND active IS NOT NULL;

-- Step 2: Make legacy columns nullable with safe defaults (Non-destructive)
-- This immediately fixes the "Field 'active' doesn't have a default value" error.
ALTER TABLE users 
   MODIFY COLUMN full_name VARCHAR(255) NULL DEFAULT NULL,
   MODIFY COLUMN active BIT(1) NULL DEFAULT b'1';

-- Step 3 (Optional - Final cleanup):
-- If no legacy external tools rely on `full_name` or `active`, run:
-- ALTER TABLE users 
--    DROP COLUMN full_name,
--    DROP COLUMN active;
