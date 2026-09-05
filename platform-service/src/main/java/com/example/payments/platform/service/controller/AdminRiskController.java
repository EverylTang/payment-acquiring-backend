package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.RiskAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/risk")
@RequiredArgsConstructor
public class AdminRiskController {
  private final RiskAdminService service;

  @GetMapping("/overview")
  @PreAuthorize("hasAuthority('risk:event:list')")
  public java.util.Map<String, Long> overview() {
    return service.overview();
  }

  @GetMapping("/events")
  @PreAuthorize("hasAuthority('risk:event:list')")
  public AdminPageResponse<RiskAdminService.RiskEventRow> events(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String level,
      @RequestParam(required = false) String merchantId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return service.events(status, level, merchantId, page, pageSize);
  }

  @GetMapping("/events/{eventId}")
  @PreAuthorize("hasAuthority('risk:event:list')")
  public RiskAdminService.RiskEventRow event(@PathVariable String eventId) {
    return service.event(eventId);
  }

  @PutMapping("/events/{eventId}/review")
  @PreAuthorize("hasAuthority('risk:event:review')")
  public void review(
      @PathVariable String eventId,
      @Valid @RequestBody RiskAdminService.ReviewRequest request,
      Authentication authentication) {
    service.review(eventId, request, authentication.getName());
  }

  @GetMapping("/lists")
  @PreAuthorize("hasAuthority('risk:list:list')")
  public AdminPageResponse<RiskAdminService.RiskListRow> lists(
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return service.lists(type, page, pageSize);
  }

  @PostMapping("/lists")
  @PreAuthorize("hasAuthority('risk:list:manage')")
  public void createList(
      @Valid @RequestBody RiskAdminService.ListRequest request, Authentication authentication) {
    service.createList(request, authentication.getName());
  }

  @PutMapping("/lists/{entryId}/status")
  @PreAuthorize("hasAuthority('risk:list:manage')")
  public void status(
      @PathVariable String entryId,
      @RequestBody java.util.Map<String, String> request,
      Authentication authentication) {
    service.changeListStatus(entryId, request.get("status"), authentication.getName());
  }
}
