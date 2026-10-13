package com.smartlms.entity;

/**
 * Roles available in SmartLMS.
 * Stored as a STRING column in the database for readability.
 * Prefixed with ROLE_ to follow Spring Security conventions.
 */
public enum Role {
    ROLE_ADMIN,
    ROLE_TEACHER,
    ROLE_STUDENT
}
