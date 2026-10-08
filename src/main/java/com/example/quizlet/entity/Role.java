package com.example.quizlet.entity;

/**
 * Application-wide user roles. Stored as a string in the database
 * so new roles can be added without a schema migration.
 */
public enum Role {
    ROLE_USER,
    ROLE_ADMIN
}
