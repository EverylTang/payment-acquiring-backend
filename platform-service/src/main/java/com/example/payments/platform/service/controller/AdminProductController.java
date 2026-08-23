package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.model.ProductModel;
import com.example.payments.platform.service.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/products")
@RequiredArgsConstructor
public class AdminProductController {
  private final ProductService productService;

  @GetMapping @PreAuthorize("hasAuthority('product:list')")
  public AdminPageResponse<ProductResponse> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
    var result=productService.list(page,pageSize);
    return new AdminPageResponse<>(result.items().stream().map(AdminProductController::response).toList(),result.page(),result.pageSize(),result.total());
  }
  @GetMapping("/{productCode}") @PreAuthorize("hasAuthority('product:detail')")
  public ProductResponse detail(@PathVariable String productCode) { return response(productService.detail(productCode)); }
  @PostMapping @PreAuthorize("hasAuthority('product:create')")
  public ProductResponse create(@Valid @RequestBody CreateRequest request,Authentication authentication) { return response(productService.create(request.productCode(),request.name(),authentication.getName(),request)); }
  @PutMapping("/{productCode}") @PreAuthorize("hasAuthority('product:update')")
  public ProductResponse update(@PathVariable String productCode,@Valid @RequestBody UpdateRequest request,Authentication authentication) { return response(productService.update(productCode,request.name(),authentication.getName(),request)); }
  @PatchMapping("/{productCode}/status") @PreAuthorize("hasAuthority('product:status')")
  public ProductResponse status(@PathVariable String productCode,@Valid @RequestBody StatusRequest request,Authentication authentication) { return response(productService.changeStatus(productCode,request.status(),authentication.getName(),request)); }
  private static ProductResponse response(ProductModel v){return new ProductResponse(v.productCode(),v.name(),v.status(),v.createdAt(),v.updatedAt());}
  public record CreateRequest(@NotBlank String productCode,@NotBlank String name){}
  public record UpdateRequest(@NotBlank String name){}
  public record StatusRequest(@Pattern(regexp="ACTIVE|DISABLED") String status){}
  public record ProductResponse(String productCode,String name,String status,Instant createdAt,Instant updatedAt){}
}
