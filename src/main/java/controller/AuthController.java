package controller;

import dto.AuthDTOs.LoginRequest;
import dto.AuthDTOs.RegisterRequest;
import dto.AuthDTOs.UserProfileResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import models.Admin;
import models.Staff;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import repository.StaffRepository;
import service.AuditService;
import service.AuthService;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

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

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        String username = (req.username() != null) ? req.username().trim() : "";
        String password = (req.password() != null) ? req.password().trim() : "";

        if (username.isEmpty() || password.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "نام کاربری و رمز عبور الزامی است."));
        }

        Optional<Staff> staffOpt = staffRepository.findByUsername(username);
        if (staffOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "کاربری با نام کاربری '" + username + "' در سیستم یافت نشد."));
        }

        Staff staff = staffOpt.get();

        if (!staff.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "حساب کاربری مسدود یا غیرفعال است."));
        }

        String storedHash = staff.getEncodedPassword();
        if ((storedHash == null || storedHash.isBlank()) && staff instanceof Admin admin) {
            storedHash = admin.getPasswordHash();
        }

        if (storedHash == null || storedHash.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "رمز عبور برای این حساب ثبت نشده است."));
        }

        if (!passwordEncoder.matches(password, storedHash)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "رمز عبور وارد شده نادرست است."));
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(staff.getUsername());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        // ثبت ورود موفق در زنجیره لاگ‌های امنیتی
        auditService.logAction(staff.getUsername(), staff.getRole(), "AUTH_LOGIN", "SUCCESS", request.getRemoteAddr(), "ورود موفقیت‌آمیز به داشبورد سامانه");

        UserProfileResponse profile = authService.getUserProfile(staff.getUsername());
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            auditService.logAction(auth.getName(), "STAFF", "AUTH_LOGOUT", "SUCCESS", request.getRemoteAddr(), "خروج کاربر از سامانه");
        }

        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(HttpServletRequest request) {
        SecurityContext context = SecurityContextHolder.getContext();
        var auth = context.getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.getUserProfile(auth.getName()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerStaff(@RequestBody RegisterRequest req) {
        try {
            Staff createdStaff = authService.registerStaffMember(req);
            auditService.logAction("ADMIN", "SUPER_ADMIN", "STAFF_REGISTERED", "SUCCESS", "127.0.0.1", "ایجاد پرسنل جدید: " + createdStaff.getUsername());
            return ResponseEntity.ok(createdStaff);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}