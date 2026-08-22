package com.example.payments.platform.service.interfaces.rest;

import java.util.List;

public record AdminPageResponse<T>(List<T> items, int page, int pageSize, long total) {}
