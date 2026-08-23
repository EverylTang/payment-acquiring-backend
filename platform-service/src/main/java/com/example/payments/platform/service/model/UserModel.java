package com.example.payments.platform.service.model;

import java.util.List;

public record UserModel(
    long id, String username, String displayName, String status, List<String> roles) {}
