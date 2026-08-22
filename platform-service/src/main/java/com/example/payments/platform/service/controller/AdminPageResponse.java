package com.example.payments.platform.service.controller;

import java.util.List;

public record AdminPageResponse<T>(List<T> items, int page, int pageSize, long total) {}
