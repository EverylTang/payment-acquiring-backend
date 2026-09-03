package com.example.payments.platform.service.model;

/** Database projection for a user row; role codes are returned by GROUP_CONCAT. */
public record UserRow(long id, String username, String displayName, String status, String roles) {}
