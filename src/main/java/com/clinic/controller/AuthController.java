package com.clinic.controller;

import com.clinic.dto.AuthDTOs.LoginRequest;
import com.clinic.dto.AuthDTOs.RegisterRequest;
import com.clinic.dto.AuthDTOs.UserProfileResponse;
import com.clinic.dto.ClinicalDTOs.StaffSummaryResponse;
import com.clinic.exceptions.InsufficientPermissionException;
import com.clinic.models.Admin;
import com.clinic.models.Staff;
import com.clinic.repository.StaffRepository;
import com.clinic.service.AuditService;
import com.clinic.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_SECONDS = 300L; // 5 minutes
    private static final long ATTEMPT_TTL_SECONDS = 900L;      // 15 minutes window
    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final String GENERIC_AUTH_ERROR = "نام کاربری یا رمز عبور نادرست است.";

    private static final Set<String> PRIVILEGED_ADMIN_ROLES = Set.of(
            "SUPER_ADMIN", "ADMIN", "FINANCIAL_ADMIN", "CLINICAL_ADMIN", "HR_ADMIN", "AUDITOR"
    );

    private record LoginAttemptState(int count, long lockedUntilEpochSecond, long lastUpdatedEpochSecond) {
    }

    private final Map<String, LoginAttemptState> failedLoginMap = new ConcurrentHashMap<>();

    private final StaffRepository staffRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;
    private final AuditService auditService;

    public AuthController(StaffRepository staffRepository,
                          AuthService authService,
                          PasswordEncoder passwordEncoder,
                          UserDetailsService userDetailsService,
                          SecurityContextRepository securityContextRepository,
                          AuditService auditService) {
        this.staffRepository = staffRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
        this.auditService = auditService;
    }

    // ==================== Authentication Endpoints ====================

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody(required = false) LoginRequest req,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        String username = (req != null && req.username() != null) ? req.username().trim() : "";
        String password = (req != null && req.password() != null) ? req.password() : "";
        String clientIp = resolveClientIp(request);

        if (username.isEmpty() || password.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "نام کاربری و رمز عبور الزامی است."));
        }

        long nowSec = Instant.now().getEpochSecond();
        String throttleKey = buildThrottleKey(username, clientIp);

        LoginAttemptState attemptState = failedLoginMap.get(throttleKey);
        if (attemptState != null && attemptState.lockedUntilEpochSecond() > nowSec) {
            long remaining = attemptState.lockedUntilEpochSecond() - nowSec;
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "تلاش‌های ناموفق بیش از حد مجاز است. لطفاً " + remaining + " ثانیه دیگر تلاش کنید."));
        }

        Optional<Staff> staffOpt = staffRepository.findByUsername(username);
        if (staffOpt.isEmpty()) {
            recordFailedAttempt(throttleKey, nowSec);
            auditService.logAction(username, "UNKNOWN", "AUTH_LOGIN", "FAILED", clientIp, "تلاش ورود ناموفق (نام کاربری نامعتبر)");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", GENERIC_AUTH_ERROR));
        }

        Staff staff = staffOpt.get();

        if (!staff.isActive() || (staff instanceof Admin admin && admin.isAccountLocked())) {
            auditService.logAction(username, staff.getRole(), "AUTH_LOGIN", "BLOCKED", clientIp, "تلاش برای ورود به حساب مسدود یا غیرفعال");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "حساب کاربری مسدود یا غیرفعال است."));
        }

        String storedHash = staff.getEncodedPassword();
        if (storedHash == null || storedHash.isBlank() || !passwordEncoder.matches(password, storedHash)) {
            recordFailedAttempt(throttleKey, nowSec);
            auditService.logAction(username, staff.getRole(), "AUTH_LOGIN", "FAILED", clientIp, "تلاش ورود ناموفق (عدم تطابق رمز)");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", GENERIC_AUTH_ERROR));
        }

        // پاک کردن شمارنده خطا پس از ورود موفق
        failedLoginMap.remove(throttleKey);

        // بازسازی سشن برای جلوگیری از Session Fixation
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        request.getSession(true);

        UserDetails userDetails = userDetailsService.loadUserByUsername(staff.getUsername());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        auditService.logAction(staff.getUsername(), staff.getRole(), "AUTH_LOGIN", "SUCCESS", clientIp, "ورود موفقیت‌آمیز به سامانه");

        UserProfileResponse profile = authService.getUserProfile(staff.getUsername());
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String clientIp = resolveClientIp(request);

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            auditService.logAction(auth.getName(), "STAFF", "AUTH_LOGOUT", "SUCCESS", clientIp, "خروج کاربر از سامانه");
        }

        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.getUserProfile(auth.getName()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerStaff(Authentication authentication,
                                           @Valid @RequestBody RegisterRequest req,
                                           HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw InsufficientPermissionException.accessDenied("ANONYMOUS", "STAFF_REGISTER");
        }

        String actorName = authentication.getName();
        String requestedRole = (req.role() != null) ? req.role().toUpperCase().replace("ROLE_", "").trim() : "";

        // بررسی سیاست امنیتی اعطای نقش (جلوگیری از ساخت حساب مدیر ارشد توسط HR_ADMIN)
        enforceRoleCreationPolicy(authentication, actorName, requestedRole, clientIp);

        Staff createdStaff = authService.registerStaffMember(req);
        auditService.logAction(actorName, "ADMIN", "STAFF_REGISTERED", "SUCCESS", clientIp,
                "ثبت پرسنل جدید: " + createdStaff.getUsername() + " با نقش " + createdStaff.getRole());

        return ResponseEntity.status(HttpStatus.CREATED).body(StaffSummaryResponse.fromEntity(createdStaff));
    }

    // ==================== Security & Rate-Limiting Helpers ====================

    private void enforceRoleCreationPolicy(Authentication authentication, String actorName, String requestedRole, String clientIp) {
        boolean isCallerSuperAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("SUPER_ADMIN") || a.equals("ROLE_SUPER_ADMIN"));

        if (PRIVILEGED_ADMIN_ROLES.contains(requestedRole) && !isCallerSuperAdmin) {
            auditService.logAction(actorName, "HR_ADMIN", "PRIVILEGE_ESCALATION_ATTEMPT", "BLOCKED", clientIp,
                    "تلاش غیرمجاز برای ساخت حساب مدیریتی با نقش: " + requestedRole);
            throw InsufficientPermissionException.accessDenied(actorName, "CREATE_ADMIN_ROLE_" + requestedRole);
        }
    }

    private String buildThrottleKey(String username, String clientIp) {
        return username.toLowerCase() + "|" + clientIp;
    }

    private void recordFailedAttempt(String key, long nowSec) {
        if (failedLoginMap.size() >= MAX_TRACKED_KEYS) {
            evictExpiredEntries(nowSec);
        }

        failedLoginMap.compute(key, (k, prev) -> {
            if (prev == null
                    || (nowSec - prev.lastUpdatedEpochSecond() > ATTEMPT_TTL_SECONDS)
                    || (prev.lockedUntilEpochSecond() > 0 && prev.lockedUntilEpochSecond() <= nowSec)) {
                return new LoginAttemptState(1, 0L, nowSec);
            }
            int nextCount = prev.count() + 1;
            long lockUntil = (nextCount >= MAX_FAILED_ATTEMPTS) ? (nowSec + LOCKOUT_DURATION_SECONDS) : 0L;
            return new LoginAttemptState(nextCount, lockUntil, nowSec);
        });
    }

    private void evictExpiredEntries(long nowSec) {
        failedLoginMap.entrySet().removeIf(entry -> {
            LoginAttemptState state = entry.getValue();
            boolean lockExpired = state.lockedUntilEpochSecond() > 0 && state.lockedUntilEpochSecond() <= nowSec;
            boolean windowExpired = (nowSec - state.lastUpdatedEpochSecond()) > ATTEMPT_TTL_SECONDS;
            return lockExpired || windowExpired;
        });
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr.trim() : "127.0.0.1";
    }
}