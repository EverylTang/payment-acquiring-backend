package com.example.payments.platform.service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import com.example.payments.platform.service.infrastructure.persistence.MybatisPlusClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwtService;
  private final MybatisPlusClient mybatisClient;

  public JwtAuthenticationFilter(JwtService jwtService, MybatisPlusClient mybatisClient) {
    this.jwtService = jwtService;
    this.mybatisClient = mybatisClient;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization != null && authorization.startsWith("Bearer ")) {
      try {
        var claims = jwtService.parse(authorization.substring(7));
        List<SimpleGrantedAuthority> roles =
            ((List<?>) claims.get("roles", List.class))
                .stream()
                    .map(String::valueOf)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        mybatisClient
            .sql(
                "SELECT DISTINCT p.permission_code FROM admin_permission p JOIN"
                    + " admin_role_permission rp ON rp.permission_id = p.id JOIN admin_role r ON"
                    + " r.id = rp.role_id JOIN admin_user_role ur ON ur.role_id = r.id JOIN"
                    + " admin_user u ON u.id = ur.user_id WHERE u.username = :username AND u.status"
                    + " = 'ACTIVE' AND p.status = 'ACTIVE'")
            .param("username", claims.getSubject())
            .query(String.class)
            .list()
            .stream()
            .map(SimpleGrantedAuthority::new)
            .forEach(roles::add);
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(claims.getSubject(), null, roles));
      } catch (RuntimeException ignored) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
