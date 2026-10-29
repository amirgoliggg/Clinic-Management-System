package com.clinic;

import com.clinic.models.AuditLog;
import com.clinic.models.MedicalRecord;
import com.clinic.models.Receipt;
import com.clinic.repository.AuditLogRepository;
import com.clinic.security.HmacKeyProvider;
import com.clinic.service.AuditService;
import com.clinic.service.AuditService.AuditChainState;
import com.clinic.service.AuditService.CrashPoint;
import com.clinic.system.SystemClock;
import org.h2.tools.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AuditProcessCrashAndConcurrencyTests {

    private static final String TEST_STRONG_AUDIT_KEY = "A9f8E7d6C5b4A39281706F5E4D3C2B1A0987654321FEDCBA0123456789ABCDEF";

    @TempDir
    Path tempDir;

    private Server h2TcpServer;
    private String sharedJdbcUrl;
    private Connection keepAliveConnection;

    @BeforeEach
    void startSharedDatabaseServer() throws Exception {
        h2TcpServer = Server.createTcpServer("-tcp", "-tcpPort", "0", "-ifNotExists").start();
        String dbName = "audit_mp_" + UUID.randomUUID().toString().replace("-", "");
        String localMemUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        keepAliveConnection = DriverManager.getConnection(localMemUrl, "sa", "");
        sharedJdbcUrl = "jdbc:h2:tcp://localhost:" + h2TcpServer.getPort() + "/mem:" + dbName;
    }

    @AfterEach
    void stopSharedDatabaseServer() throws Exception {
        if (keepAliveConnection != null && !keepAliveConnection.isClosed()) {
            keepAliveConnection.close();
        }
        if (h2TcpServer != null && h2TcpServer.isRunning(false)) {
            h2TcpServer.stop();
        }
    }

    @Configuration
    @EnableAutoConfiguration(exclude = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    })
    @EnableTransactionManagement
    @EntityScan(basePackageClasses = AuditLog.class)
    @EnableJpaRepositories(basePackageClasses = AuditLogRepository.class)
    @Import(AuditService.class)
    public static class MinimalAuditWorkerConfig {
    }

    public static class ChildAuditWorker {
        public static void main(String[] args) {
            String jdbcUrl = args[0];
            String anchorPath = args[1];
            String mode = args[2];
            String actionName = args[3];
            String crashPoint = args.length > 4 ? args[4] : "NONE";

            // تنظیم از طریق System Property و Command-Line Args برای غلبه قطعی بر application.properties
            System.setProperty("clinic.security.hmac.audit-secret", TEST_STRONG_AUDIT_KEY);
            System.setProperty("clinic.audit.lock-timeout-ms", "30000");
            System.setProperty("clinic.audit.crash-point", crashPoint);
            System.setProperty("spring.datasource.url", jdbcUrl);
            System.setProperty("spring.datasource.username", "sa");
            System.setProperty("spring.datasource.password", "");
            System.setProperty("spring.jpa.hibernate.ddl-auto", "update");
            System.setProperty("clinic.audit.anchor-path", anchorPath);

            String[] springArgs = new String[]{
                    "--spring.main.banner-mode=off",
                    "--logging.level.root=ERROR",
                    "--spring.datasource.url=" + jdbcUrl,
                    "--spring.datasource.username=sa",
                    "--spring.datasource.password=",
                    "--spring.jpa.hibernate.ddl-auto=update",
                    "--clinic.audit.anchor-path=" + anchorPath
            };

            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(MinimalAuditWorkerConfig.class)
                    .web(WebApplicationType.NONE)
                    .logStartupInfo(false)
                    .run(springArgs)) {

                AuditService auditService = ctx.getBean(AuditService.class);
                PlatformTransactionManager txManager = ctx.getBean(PlatformTransactionManager.class);
                TransactionTemplate tx = new TransactionTemplate(txManager);

                if ("APPEND".equals(mode)) {
                    tx.executeWithoutResult(status ->
                            auditService.logAction("worker", "CORE", actionName, "SUCCESS", "127.0.0.1", "child-process-entry")
                    );
                } else if ("RECOVER_AND_VERIFY".equals(mode)) {
                    // غیرفعال‌سازی نقطه کرش در مرحله بازیابی
                    System.setProperty("clinic.audit.crash-point", CrashPoint.NONE.name());
                    AuditChainState stateBefore = auditService.diagnoseChainState();
                    if (stateBefore == AuditChainState.RECOVERY_PENDING_COMMITTED
                            || stateBefore == AuditChainState.RECOVERY_PENDING_ROLLED_BACK) {
                        tx.executeWithoutResult(status -> auditService.recoverPendingAnchor());
                    }
                    if (!auditService.verifyChainIntegrity()) {
                        System.exit(42);
                    }
                }
            }
        }
    }

    private Process launchChildWorker(Path anchorFile, String mode, String action, CrashPoint crashPoint) throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(classpath);
        command.add(ChildAuditWorker.class.getName());
        command.add(sharedJdbcUrl);
        command.add(anchorFile.toAbsolutePath().toString());
        command.add(mode);
        command.add(action);
        command.add(crashPoint.name());

        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();
    }

    private int runAndAwaitChild(Path anchorFile, String mode, String action, CrashPoint crashPoint) throws Exception {
        Process proc = launchChildWorker(anchorFile, mode, action, crashPoint);
        try {
            boolean exited = proc.waitFor(30, TimeUnit.SECONDS);
            assertTrue(exited, "پروسه فرزند در زمان مقرر پایان نیافت");
            return proc.exitValue();
        } finally {
            if (proc.isAlive()) {
                proc.destroyForcibly();
                proc.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    // ==================== آزمایش ب: قطع ناگهانی فرایند در لحظات حساس ====================

    @Test
    @DisplayName("ب-۱) قطع ناگهانی پروسه (halt) دقیقاً پس از نوشتن فایل .pending و پیش از Commit دیتابیس")
    void abruptProcessCrashBeforeDatabaseCommitMustRecoverCleanly() throws Exception {
        Path anchorFile = tempDir.resolve("crash_1.anchor");
        Path pendingFile = tempDir.resolve("crash_1.anchor.pending");

        assertEquals(0, runAndAwaitChild(anchorFile, "APPEND", "INIT_OK", CrashPoint.NONE));

        assertEquals(86, runAndAwaitChild(
                anchorFile, "APPEND", "CRASH_BEFORE_COMMIT", CrashPoint.AFTER_PENDING_WRITE_BEFORE_DB_SAVE
        ));
        assertTrue(Files.exists(pendingFile), "فایل شواهد .pending باید روی دیسک باقی مانده باشد");

        assertEquals(0, runAndAwaitChild(anchorFile, "RECOVER_AND_VERIFY", "VERIFY", CrashPoint.NONE));
        assertFalse(Files.exists(pendingFile));
        try (var files = Files.list(tempDir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().contains(".rolled-back-")),
                    "فایل شواهد تراکنش ناتمام باید بدون حذف شدن، آرشیو شده باشد");
        }
    }

    @Test
    @DisplayName("ب-۲) قطع ناگهانی پروسه (halt) دقیقاً پس از Commit دیتابیس و پیش از به‌روزرسانی فایل .anchor")
    void abruptProcessCrashAfterDatabaseCommitBeforeAnchorPublishMustRecoverWithoutLoss() throws Exception {
        Path anchorFile = tempDir.resolve("crash_2.anchor");
        Path pendingFile = tempDir.resolve("crash_2.anchor.pending");

        assertEquals(0, runAndAwaitChild(anchorFile, "APPEND", "INIT_OK", CrashPoint.NONE));

        assertEquals(86, runAndAwaitChild(
                anchorFile, "APPEND", "CRASH_AFTER_DB_COMMIT", CrashPoint.AFTER_DB_COMMIT_BEFORE_ANCHOR_PUBLISH
        ));
        assertTrue(Files.exists(pendingFile), "ژورنال .pending باید برای اثبات تراکنش Commit شده موجود باشد");
        assertTrue(Files.readString(anchorFile).startsWith("1:"), "فایل لنگر قبل از بازیابی هنوز روی رکورد ۱ است");

        assertEquals(0, runAndAwaitChild(anchorFile, "RECOVER_AND_VERIFY", "VERIFY", CrashPoint.NONE));
        assertFalse(Files.exists(pendingFile));
        assertTrue(Files.readString(anchorFile).startsWith("2:"), "لنگر نهایی باید به رکورد شماره ۲ ارتقا یافته باشد");
    }

    @Test
    @DisplayName("ب-۳) قطع ناگهانی پروسه (halt) پس از نوشتن .anchor و پیش از پاک شدن فایل .pending")
    void abruptProcessCrashAfterAnchorPublishBeforePendingCleanupMustRecoverIdempotently() throws Exception {
        Path anchorFile = tempDir.resolve("crash_3.anchor");
        Path pendingFile = tempDir.resolve("crash_3.anchor.pending");

        assertEquals(86, runAndAwaitChild(
                anchorFile, "APPEND", "CRASH_BEFORE_CLEANUP", CrashPoint.AFTER_ANCHOR_PUBLISH_BEFORE_PENDING_DELETE
        ));
        assertTrue(Files.exists(pendingFile));
        assertTrue(Files.exists(anchorFile));

        assertEquals(0, runAndAwaitChild(anchorFile, "RECOVER_AND_VERIFY", "VERIFY", CrashPoint.NONE));
        assertFalse(Files.exists(pendingFile));
        assertTrue(Files.readString(anchorFile).startsWith("1:"));
    }

    // ==================== آزمایش ج: اجرای هم‌زمان چند فرایند مستقل ====================

    @Test
    @DisplayName("ج) اجرای هم‌زمان چند پروسه مستقل سیستم‌عامل (Multi-JVM) بدون خرابی زنجیره حسابرسی")
    void concurrentIndependentProcessesMustPreserveAuditChainIntegrity() throws Exception {
        Path anchorFile = tempDir.resolve("multiprocess.anchor");

        assertEquals(0, runAndAwaitChild(anchorFile, "APPEND", "BOOTSTRAP", CrashPoint.NONE));

        Process p1 = launchChildWorker(anchorFile, "APPEND", "PROC_1_ACTION", CrashPoint.NONE);
        Process p2 = launchChildWorker(anchorFile, "APPEND", "PROC_2_ACTION", CrashPoint.NONE);
        Process p3 = launchChildWorker(anchorFile, "APPEND", "PROC_3_ACTION", CrashPoint.NONE);

        try {
            assertTrue(p1.waitFor(45, TimeUnit.SECONDS));
            assertTrue(p2.waitFor(45, TimeUnit.SECONDS));
            assertTrue(p3.waitFor(45, TimeUnit.SECONDS));

            assertEquals(0, p1.exitValue());
            assertEquals(0, p2.exitValue());
            assertEquals(0, p3.exitValue());
        } finally {
            if (p1.isAlive()) p1.destroyForcibly();
            if (p2.isAlive()) p2.destroyForcibly();
            if (p3.isAlive()) p3.destroyForcibly();
        }

        assertEquals(0, runAndAwaitChild(anchorFile, "RECOVER_AND_VERIFY", "VERIFY", CrashPoint.NONE));
        assertTrue(Files.readString(anchorFile).startsWith("4:"), "هر ۴ تراکنش پروسه‌های موازی باید به ترتیب در زنجیره ثبت شده باشند");
    }

    // ==================== آزمایش د: امنیت کلیدهای HMAC و حالت سخت‌گیرانه ====================

    @Test
    @DisplayName("د-۱) رد شدن کلیدهای پیش‌فرض قدیمی و کلیدهای کوتاه در HmacKeyProvider")
    void compromisedDefaultKeysAndShortKeysMustBeRejected() {
        String prevAuditSecret = System.getProperty("clinic.security.hmac.audit-secret");
        String prevStrict = System.getProperty("clinic.security.strict-hmac-keys");
        try {
            System.setProperty("clinic.security.hmac.audit-secret", "CLINIC_AUDIT_CHAIN_HMAC_SECRET_KEY_2026");
            assertThrows(IllegalStateException.class, HmacKeyProvider::getAuditKey,
                    "کلید پیش‌فرض قدیمی باید به عنوان کلید افشاشده مسدود شود");

            System.setProperty("clinic.security.hmac.audit-secret", "short-key-123");
            assertThrows(IllegalStateException.class, HmacKeyProvider::getAuditKey,
                    "کلیدهای کوتاه‌تر از ۲۵۶ بیت باید رد شوند");

            System.clearProperty("clinic.security.hmac.audit-secret");
            System.setProperty("clinic.security.strict-hmac-keys", "true");
            if (System.getenv("CLINIC_AUDIT_HMAC_SECRET") == null) {
                assertThrows(IllegalStateException.class, HmacKeyProvider::getAuditKey,
                        "در حالت عملیاتی بدون کلید خارجی باید خطا پرتاب شود");
            }
        } finally {
            if (prevAuditSecret != null) {
                System.setProperty("clinic.security.hmac.audit-secret", prevAuditSecret);
            } else {
                System.clearProperty("clinic.security.hmac.audit-secret");
            }
            if (prevStrict != null) {
                System.setProperty("clinic.security.strict-hmac-keys", prevStrict);
            } else {
                System.clearProperty("clinic.security.strict-hmac-keys");
            }
        }
    }

    @Test
    @DisplayName("د-۲) تشخیص جعل امضا در صورت تغییر کلید HMAC برای رسید و پرونده پزشکی")
    void rotatingOrForgingHmacKeyMustInvalidateSignatureVerification() {
        String prevReceiptKey = System.getProperty("clinic.security.hmac.receipt-secret");
        String prevMrKey = System.getProperty("clinic.security.hmac.medical-record-secret");
        try {
            System.setProperty("clinic.security.hmac.receipt-secret",
                    "1111111111111111111111111111111122222222222222222222222222222222");
            System.setProperty("clinic.security.hmac.medical-record-secret",
                    "3333333333333333333333333333333344444444444444444444444444444444");

            Receipt receipt = new Receipt("POS-SEC1", "1234567890", "DOC-1", "cashier",
                    new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                    Receipt.PaymentMethod.CASH, Receipt.ReceiptStatus.PAID, "test", SystemClock.now());
            MedicalRecord record = MedicalRecord.create("MR-SEC1", "1234567890", "DOC-1", "Diag", "Rx", "Notes");

            assertTrue(receipt.verifyIntegrity());
            assertTrue(record.verifyIntegrity());

            System.setProperty("clinic.security.hmac.receipt-secret",
                    "9999999999999999999999999999999988888888888888888888888888888888");
            System.setProperty("clinic.security.hmac.medical-record-secret",
                    "7777777777777777777777777777777766666666666666666666666666666666");

            assertFalse(receipt.verifyIntegrity(), "رسید امضاشده با کلید دیگر نباید معتبر شناخته شود");
            assertFalse(record.verifyIntegrity(), "پرونده امضاشده با کلید دیگر نباید معتبر شناخته شود");
        } finally {
            if (prevReceiptKey != null) {
                System.setProperty("clinic.security.hmac.receipt-secret", prevReceiptKey);
            } else {
                System.clearProperty("clinic.security.hmac.receipt-secret");
            }
            if (prevMrKey != null) {
                System.setProperty("clinic.security.hmac.medical-record-secret", prevMrKey);
            } else {
                System.clearProperty("clinic.security.hmac.medical-record-secret");
            }
        }
    }
}