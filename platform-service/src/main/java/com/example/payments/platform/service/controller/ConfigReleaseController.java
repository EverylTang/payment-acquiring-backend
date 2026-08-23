package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.admin.ConfigReleaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/config-releases")
@RequiredArgsConstructor
public class ConfigReleaseController {
  private final ConfigReleaseService service;

  @GetMapping
  public AdminPageResponse<ConfigReleaseService.ReleaseResponse> list(
      @RequestParam(defaultValue = "1") int p, @RequestParam(defaultValue = "20") int s) {
    return service.list(p, s);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  public ConfigReleaseService.ReleaseResponse create(
      @Valid @RequestBody ConfigReleaseService.CreateReleaseRequest r, Authentication a) {
    return service.create(r, a);
  }

  @PostMapping("/{id}/submit")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  public ConfigReleaseService.ReleaseResponse submit(
      @PathVariable("id") String id,
      @Valid @RequestBody ConfigReleaseService.ReasonRequest r,
      Authentication a) {
    return service.submit(id, r, a);
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasRole('ADMIN')")
  public ConfigReleaseService.ReleaseResponse approve(
      @PathVariable("id") String id,
      @RequestBody ConfigReleaseService.ReasonRequest r,
      Authentication a) {
    return service.approve(id, r, a);
  }

  @PostMapping("/{id}/publish")
  @PreAuthorize("hasRole('ADMIN')")
  public ConfigReleaseService.ReleaseResponse publish(
      @PathVariable("id") String id,
      @RequestBody ConfigReleaseService.ReasonRequest r,
      Authentication a) {
    return service.publish(id, r, a);
  }

  @GetMapping("/{id}/diff")
  public java.util.Map<String, Object> diff(@PathVariable("id") String id) {
    return service.diff(id);
  }

  @PostMapping("/{id}/rollback")
  @PreAuthorize("hasRole('ADMIN')")
  public ConfigReleaseService.ReleaseResponse rollback(
      @PathVariable("id") String id,
      @Valid @RequestBody ConfigReleaseService.ReasonRequest r,
      Authentication a) {
    return service.rollback(id, r, a);
  }
}
