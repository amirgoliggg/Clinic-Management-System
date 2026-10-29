package com.clinic.security;

import com.clinic.models.Admin;
import com.clinic.models.Staff;
import com.clinic.repository.StaffRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Revalidate cached session privileges after account suspension, deletion or role changes. */
public class ActiveStaffSessionFilter extends OncePerRequestFilter {
    private final StaffRepository staffRepository;

    public ActiveStaffSessionFilter(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            Staff staff = staffRepository.findByUsername(auth.getName()).orElse(null);
            String role = staff instanceof Admin admin ? admin.getAdminRole().name()
                    : staff != null ? staff.getRole().replace("ROLE_", "") : "";
            boolean hasCurrentRole = auth.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals(role)
                            || authority.getAuthority().equals("ROLE_" + role));
            if (staff == null || !staff.isActive()
                    || staff instanceof Admin admin && admin.isAccountLocked() || !hasCurrentRole) {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) {
                    request.getSession(false).invalidate();
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"status\":401,\"error\":\"SESSION_REVOKED\",\"message\":\"نشست کاربری نامعتبر شده است. لطفاً دوباره وارد شوید.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}