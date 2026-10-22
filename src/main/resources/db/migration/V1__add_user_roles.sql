-- Existing accounts keep their IDs and password hashes and receive the USER role.
-- Safe to run again: an existing ADMIN role is never overwritten.
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(5) NOT NULL DEFAULT 'USER';
