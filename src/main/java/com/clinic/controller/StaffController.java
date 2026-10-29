package com.clinic.controller;

import com.clinic.dto.AuthDTOs.RegisterRequest;
import com.clinic.dto.AuthDTOs.RoleDefinitionDTO;
import com.clinic.dto.ClinicalDTOs.StaffSummaryResponse;
import com.clinic.exceptions.InsufficientPermissionException;
import com.clinic.models.Staff;
import com.clinic.service.AuditService;
import com.clinic.service.AuthService;
import com.clinic.service.StaffManagementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin/staff")
public class StaffController {

    private static final Set<String> PRIVILEGED_ADMIN_ROLES = Set.of(
            "SUPER_ADMIN", "ADMIN", "FINANCIAL_ADMIN", "CLINICAL_ADMIN", "HR_ADMIN", "AUDITOR"
    );

    private static final Path KEYSTORE_PATH = Paths.get("data", "clinic-keystore.p12");
    private static final Path CERT_EXPORT_PATH = Paths.get("data", "clinic-lan-ca.cer");

    private final StaffManagementService staffManagementService;
    private final AuthService authService;
    private final AuditService auditService;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    public StaffController(StaffManagementService staffManagementService,
                           AuthService authService,
                           AuditService auditService) {
        this.staffManagementService = staffManagementService;
        this.authService = authService;
        this.auditService = auditService;
    }

    // ==================== مدیریت و ساخت نقش‌های سازمانی ====================

    @GetMapping("/roles")
    public ResponseEntity<List<RoleDefinitionDTO>> getAvailableRoles() {
        return ResponseEntity.ok(authService.getAllAvailableRoles());
    }

    @PostMapping("/roles")
    public ResponseEntity<RoleDefinitionDTO> createNewRole(
            Authentication authentication,
            @Valid @RequestBody RoleDefinitionDTO req) {
        enforceAdminCreationPolicy(authentication, req.baseClearance());
        RoleDefinitionDTO created = authService.createCustomRole(req);
        auditService.logCurrentActorAction("ADMIN", "ROLE_CREATED", "SUCCESS",
                "تعریف نقش سازمانی جدید: " + created.roleCode() + " (" + created.displayName() + ") با سطح دسترسی " + created.baseClearance());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ==================== مدیریت شبکه داخلی و گواهی SSL اختیاری ====================

    @GetMapping("/network-status")
    public ResponseEntity<Object> getNetworkAndSslStatus() {
        List<String> lanIps = detectLocalLanIps();
        String primaryIp = lanIps.isEmpty() ? "127.0.0.1" : lanIps.get(0);
        String protocol = sslEnabled ? "https" : "http";

        return ResponseEntity.ok(Map.of(
                "protocol", protocol.toUpperCase(),
                "port", serverPort,
                "sslEnabled", sslEnabled,
                "keystoreExists", Files.exists(KEYSTORE_PATH),
                "primaryLanUrl", protocol + "://" + primaryIp + ":" + serverPort,
                "lanIps", lanIps,
                "installGuides", getCrossPlatformInstallCommands()
        ));
    }

    @PostMapping("/ssl-generate")
    public ResponseEntity<Object> generateOptionalSslBundle(Authentication authentication) {
        enforceAdminCreationPolicy(authentication, "SUPER_ADMIN");
        List<String> lanIps = detectLocalLanIps();
        String primaryIp = lanIps.isEmpty() ? "127.0.0.1" : lanIps.get(0);
        String certPath = generateSelfSignedBundle(primaryIp);

        auditService.logCurrentActorAction("SUPER_ADMIN", "SSL_BUNDLE_GENERATED", "SUCCESS",
                "تولید بسته گواهی SSL شبکه داخلی در مسیر: " + certPath);

        return ResponseEntity.ok(Map.of(
                "generated", true,
                "certFilePath", certPath,
                "activationHint", "برای فعال‌سازی HTTPS، متغیر محیطی CLINIC_SSL_ENABLED=true و SERVER_PORT=8443 را تنظیم کنید.",
                "installGuides", getCrossPlatformInstallCommands()
        ));
    }

    // ==================== مدیریت پرسنل ====================

    @PostMapping("/register")
    public ResponseEntity<StaffSummaryResponse> registerStaff(
            Authentication authentication,
            @Valid @RequestBody RegisterRequest req) {

        enforceAdminCreationPolicy(authentication, authService.resolveBaseClearance(req.role()));
        Staff created = authService.registerStaffMember(req);

        auditService.logCurrentActorAction("ADMIN", "STAFF_REGISTERED", "SUCCESS",
                "ثبت پرسنل جدید از پنل مدیریت: " + created.getUsername() + " با نقش " + created.getRole());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StaffSummaryResponse.fromEntity(created));
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<StaffSummaryResponse> getStaffByUsername(@PathVariable String username) {
        Staff staff = staffManagementService.getStaffByUsername(username.trim());
        return ResponseEntity.ok(StaffSummaryResponse.fromEntity(staff));
    }

    @GetMapping("/nid/{nationalId}")
    public ResponseEntity<StaffSummaryResponse> getStaffByNationalId(@PathVariable String nationalId) {
        Staff staff = staffManagementService.getStaffByNationalId(nationalId.trim());
        return ResponseEntity.ok(StaffSummaryResponse.fromEntity(staff));
    }

    @GetMapping
    public ResponseEntity<List<StaffSummaryResponse>> getAllStaff() {
        List<StaffSummaryResponse> list = staffManagementService.getAllStaff()
                .stream()
                .map(StaffSummaryResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<StaffSummaryResponse>> getStaffByRole(@PathVariable String role) {
        List<StaffSummaryResponse> list = staffManagementService.getStaffByRole(role.trim())
                .stream()
                .map(StaffSummaryResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PutMapping("/{username}/status")
    public ResponseEntity<Void> toggleStaffStatus(
            Authentication authentication,
            @PathVariable String username,
            @RequestParam boolean isActive) {

        staffManagementService.toggleStaffStatus(authentication, username.trim(), isActive);
        return ResponseEntity.ok().build();
    }

    // ==================== متدهای کمکی امنیتی و شبکه ====================

    private void enforceAdminCreationPolicy(Authentication authentication, String requestedRoleRaw) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw InsufficientPermissionException.accessDenied("ANONYMOUS", "STAFF_REGISTER");
        }

        String normalizedRole = (requestedRoleRaw != null)
                ? requestedRoleRaw.toUpperCase().replace("ROLE_", "").trim()
                : "";

        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("SUPER_ADMIN") || a.equals("ROLE_SUPER_ADMIN"));

        if (PRIVILEGED_ADMIN_ROLES.contains(normalizedRole) && !isSuperAdmin) {
            auditService.logCurrentActorAction("HR_ADMIN", "PRIVILEGE_ESCALATION_ATTEMPT", "BLOCKED",
                    "تلاش غیرمجاز در StaffController برای ساخت نقش مدیریتی: " + normalizedRole);
            throw InsufficientPermissionException.accessDenied(authentication.getName(), "CREATE_ADMIN_ROLE_" + normalizedRole);
        }
    }

    private List<String> detectLocalLanIps() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return ips;
    }

    private synchronized String generateSelfSignedBundle(String lanIp) {
        try {
            Files.createDirectories(KEYSTORE_PATH.getParent());
            String isWin = System.getProperty("os.name", "").toLowerCase().contains("win") ? ".exe" : "";
            String keytoolBin = Path.of(System.getProperty("java.home"), "bin", "keytool" + isWin).toString();

            String sanExt = "SAN=dns:localhost,ip:127.0.0.1" +
                    ((lanIp != null && !lanIp.isBlank() && !"127.0.0.1".equals(lanIp)) ? ",ip:" + lanIp.trim() : "");

            if (!Files.exists(KEYSTORE_PATH)) {
                Process genProc = new ProcessBuilder(
                        keytoolBin, "-genkeypair",
                        "-alias", "clinic-lan-ssl",
                        "-keyalg", "RSA", "-keysize", "4096",
                        "-storetype", "PKCS12",
                        "-keystore", KEYSTORE_PATH.toString(),
                        "-validity", "3650",
                        "-storepass", "ClinicSSL@2026",
                        "-dname", "CN=Clinic-LAN-Server, OU=MedicalIT, O=Clinic, L=Tehran, C=IR",
                        "-ext", sanExt
                ).redirectErrorStream(true).start();
                genProc.waitFor(20, TimeUnit.SECONDS);
            }

            if (Files.exists(KEYSTORE_PATH)) {
                Files.deleteIfExists(CERT_EXPORT_PATH);
                Process expProc = new ProcessBuilder(
                        keytoolBin, "-exportcert",
                        "-alias", "clinic-lan-ssl",
                        "-keystore", KEYSTORE_PATH.toString(),
                        "-storepass", "ClinicSSL@2026",
                        "-rfc", "-file", CERT_EXPORT_PATH.toString()
                ).redirectErrorStream(true).start();
                expProc.waitFor(15, TimeUnit.SECONDS);
            }

            return CERT_EXPORT_PATH.toAbsolutePath().toString();
        } catch (Exception e) {
            throw new IllegalStateException("خطا در تولید بسته گواهی SSL: " + e.getMessage(), e);
        }
    }

    private List<String> getCrossPlatformInstallCommands() {
        String certPath = CERT_EXPORT_PATH.toAbsolutePath().toString();
        return List.of(
                "Windows (PowerShell Admin): Import-Certificate -FilePath \"" + certPath + "\" -CertStoreLocation Cert:\\LocalMachine\\Root",
                "macOS (Terminal): sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain \"" + certPath + "\"",
                "Linux (Ubuntu/Debian): sudo cp \"" + certPath + "\" /usr/local/share/ca-certificates/clinic.crt && sudo update-ca-certificates"
        );
    }
}