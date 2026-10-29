package com.clinic.config;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * سرویس اختیاری و تقاضامحور تولید گواهی رمزنگاری شبکه داخلی (سازگار با Windows، Linux و macOS).
 * به صورت پیش‌فرض غیرفعال است و تنها در صورت درخواست صریح مدیر ارشد اجرا می‌شود.
 */
@Service
public class AutoSslTrustInstaller {

    private static final Path KEYSTORE_PATH = Paths.get("data", "clinic-keystore.p12");
    private static final Path CERT_EXPORT_PATH = Paths.get("data", "clinic-lan-ca.cer");

    public boolean isKeystoreGenerated() {
        return Files.exists(KEYSTORE_PATH);
    }

    public Path getCertExportPath() {
        return CERT_EXPORT_PATH.toAbsolutePath();
    }

    /**
     * تولید گواهی PKCS12 و استخراج فایل عمومی .cer به صورت مستقل از سیستم‌عامل (بدون اجرای certutil).
     */
    public synchronized String generateSelfSignedBundle(String lanIp) {
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

    public List<String> getCrossPlatformInstallCommands() {
        String certPath = CERT_EXPORT_PATH.toAbsolutePath().toString();
        return List.of(
                "Windows (PowerShell Admin): Import-Certificate -FilePath \"" + certPath + "\" -CertStoreLocation Cert:\\LocalMachine\\Root",
                "macOS (Terminal): sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain \"" + certPath + "\"",
                "Linux (Ubuntu/Debian): sudo cp \"" + certPath + "\" /usr/local/share/ca-certificates/clinic.crt && sudo update-ca-certificates"
        );
    }
}