package com.clinic.config;

import com.clinic.repository.StaffRepository;
import com.clinic.security.ActiveStaffSessionFilter;
import com.clinic.security.LanSecurityFilter;
import com.clinic.service.AuditService;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${clinic.network.allowed-subnet-prefix:}")
    private String allowedSubnetPrefix;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   StaffRepository staffRepository,
                                                   AuditService auditService) throws Exception {
        http
                .addFilterBefore(new LanSecurityFilter(auditService, allowedSubnetPrefix, Set.of()), SecurityContextHolderFilter.class)
                .addFilterAfter(new ActiveStaffSessionFilter(staffRepository), SecurityContextHolderFilter.class)
                .securityContext(context -> context.securityContextRepository(securityContextRepository()))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/styles.css",
                                "/app.js",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico",
                                "/error",
                                "/api/auth/login",
                                "/api/auth/me",
                                "/api/auth/logout",
                                "/api/auth/csrf",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers("/h2-console/**").hasAnyAuthority("SUPER_ADMIN", "ROLE_SUPER_ADMIN")

                        // ۱. مدیریت پرسنل: فقط مدیر ارشد و مدیر منابع انسانی
                        .requestMatchers(HttpMethod.POST, "/api/auth/register")
                        .hasAnyAuthority("SUPER_ADMIN", "ROLE_SUPER_ADMIN", "HR_ADMIN", "ROLE_HR_ADMIN")
                        .requestMatchers("/api/admin/staff/**")
                        .hasAnyAuthority("SUPER_ADMIN", "ROLE_SUPER_ADMIN", "HR_ADMIN", "ROLE_HR_ADMIN")

                        // ۲. حسابرسی و لاگ‌های امنیتی: فقط مدیر ارشد و حسابرس (Auditor)
                        .requestMatchers("/api/admin/audit/**")
                        .hasAnyAuthority("SUPER_ADMIN", "ROLE_SUPER_ADMIN", "AUDITOR", "ROLE_AUDITOR")

                        // ۳. تریاژ و خدمات پرستاری: مخصوص پرستار؛ اما معاینه و اصلاح پرونده فقط مخصوص پزشک
                        .requestMatchers(HttpMethod.POST, "/api/clinical/records/triage", "/api/clinical/records/care")
                        .hasAnyAuthority("NURSE", "ROLE_NURSE")
                        .requestMatchers(HttpMethod.POST, "/api/clinical/records/**")
                        .hasAnyAuthority("DOCTOR", "ROLE_DOCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/clinical/records/**")
                        .hasAnyAuthority("DOCTOR", "ROLE_DOCTOR")
                        .requestMatchers(HttpMethod.GET, "/api/clinical/records/**")
                        .hasAnyAuthority("DOCTOR", "ROLE_DOCTOR", "NURSE", "ROLE_NURSE", "CLINICAL_ADMIN", "ROLE_CLINICAL_ADMIN", "SUPER_ADMIN", "ROLE_SUPER_ADMIN")

                        // ۴. پذیرش، تغییر وضعیت و صف بیماران
                        .requestMatchers(HttpMethod.POST, "/api/clinical/patients/register")
                        .hasAnyAuthority("RECEPTIONIST", "ROLE_RECEPTIONIST", "NURSE", "ROLE_NURSE", "SUPER_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers("/api/clinical/patients/**")
                        .hasAnyAuthority("RECEPTIONIST", "ROLE_RECEPTIONIST", "NURSE", "ROLE_NURSE", "DOCTOR", "ROLE_DOCTOR", "CASHIER", "ROLE_CASHIER", "CLINICAL_ADMIN", "ROLE_CLINICAL_ADMIN", "SUPER_ADMIN", "ROLE_SUPER_ADMIN")

                        // ۵. امور مالی و صندوق: تسویه فقط توسط صندوق‌دار؛ استرداد و گزارش‌ها توسط مدیر مالی و مدیر ارشد
                        .requestMatchers(HttpMethod.POST, "/api/billing/settle")
                        .hasAnyAuthority("CASHIER", "ROLE_CASHIER")
                        .requestMatchers("/api/billing/**")
                        .hasAnyAuthority("CASHIER", "ROLE_CASHIER", "FINANCIAL_ADMIN", "ROLE_FINANCIAL_ADMIN", "SUPER_ADMIN", "ROLE_SUPER_ADMIN")

                        .anyRequest().authenticated()
                )
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                        "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                        "font-src 'self' data: https://cdn.jsdelivr.net; " +
                                        "img-src 'self' data:; " +
                                        "script-src 'self' 'unsafe-inline'"
                        ))
                );

        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // افزایش فاکتور هزینه هش BCrypt به ۱۲ برای امنیت بالاتر
    }
}