package com.clinic.security;

import com.clinic.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Set;

/**
 * فایروال لایه اپلیکیشن برای شبکه داخلی (LAN/WLAN) و قفل رمزنگاری نشست روی IP ایستگاه کاری.
 * ۱. مسدودسازی هرگونه درخواست خارج از شبکه محلی کلینیک.
 * ۲. جلوگیری از سرقت کوکی نشست (Session Hijacking) در شبکه وای‌فای با تطبیق IP مبدا در تمام درخواست‌ها.
 */
public class LanSecurityFilter extends OncePerRequestFilter {

    public static final String SESSION_BOUND_IP_ATTR = "CLINIC_BOUND_CLIENT_IP";

    private final AuditService auditService;
    private final String allowedSubnetPrefix;
    private final Set<String> blockedIps;

    public LanSecurityFilter(AuditService auditService, String allowedSubnetPrefix, Set<String> blockedIps) {
        this.auditService = auditService;
        this.allowedSubnetPrefix = (allowedSubnetPrefix != null) ? allowedSubnetPrefix.trim() : "";
        this.blockedIps = (blockedIps != null) ? Set.copyOf(blockedIps) : Set.of();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = normalizeIp(request.getRemoteAddr());

        // ۱. بررسی لیست سیاه و بررسی تعلق IP به شبکه خصوصی کلینیک (LAN / WLAN)
        if (blockedIps.contains(clientIp) || !isAllowedLanAddress(clientIp)) {
            denyRequest(response, HttpServletResponse.SC_FORBIDDEN, "LAN_FIREWALL_BLOCKED",
                    "دسترسی مسدود شد: دستگاه شما (" + clientIp + ") در محدوده شبکه مجاز کلینیک قرار ندارد.");
            return;
        }

        // ۲. بررسی قفل نشست به IP (جلوگیری از Session Hijacking در شبکه بی‌سیم)
        HttpSession session = request.getSession(false);
        if (session != null) {
            String boundIp = (String) session.getAttribute(SESSION_BOUND_IP_ATTR);
            if (boundIp == null) {
                session.setAttribute(SESSION_BOUND_IP_ATTR, clientIp);
            } else if (!boundIp.equals(clientIp)) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                String username = (auth != null) ? auth.getName() : "UNKNOWN";

                SecurityContextHolder.clearContext();
                session.invalidate();

                if (auditService != null) {
                    auditService.logAction(username, "SECURITY", "LAN_SESSION_HIJACK_BLOCKED", "BLOCKED", clientIp,
                            "تلاش برای استفاده از نشست فعالِ ایستگاه [" + boundIp + "] از طریق دستگاه غیرمجاز [" + clientIp + "]");
                }

                denyRequest(response, HttpServletResponse.SC_UNAUTHORIZED, "SESSION_IP_MISMATCH",
                        "خطای امنیتی شبکه: تغییر غیرمجاز آدرس IP ایستگاه کاری تشخیص داده شد. نشست شما مسدود گردید.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowedLanAddress(String ip) {
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return true;
        }
        // اگر مدیر کلینیک پیشوند خاصی مثل "192.168.10." را تعیین کرده باشد، فقط همان زیرشبکه مجاز است
        if (!allowedSubnetPrefix.isEmpty() && !"*".equals(allowedSubnetPrefix)) {
            return ip.startsWith(allowedSubnetPrefix);
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalizeIp(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return "127.0.0.1";
        }
        String trimmed = rawIp.trim();
        return ("0:0:0:0:0:0:0:1".equals(trimmed) || "::1".equals(trimmed)) ? "127.0.0.1" : trimmed;
    }

    private static void denyRequest(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format(
                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}", status, code, message
        ));
    }
}