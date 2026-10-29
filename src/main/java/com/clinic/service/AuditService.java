package com.clinic.service;

import com.clinic.models.AuditLog;
import com.clinic.repository.AuditLogRepository;
import com.clinic.security.HmacKeyProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AuditService {

    public static final String GENESIS_PREVIOUS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * قفل درون‌پروسه‌ای (Per-Anchor Path) برای جلوگیری از خطای OverlappingFileLockException
     * و باگ آزاد شدن قفل POSIX هنگام بسته شدن FileChannel در نخ‌های موازی یک JVM.
     */
    private static final ConcurrentMap<Path, ReentrantLock> IN_PROCESS_LOCKS = new ConcurrentHashMap<>();

    /**
     * وضعیت‌های دقیق سلامت و همگام‌سازی بین دیتابیس و فایل‌سیستم.
     */
    public enum AuditChainState {
        INTACT("زنجیره حسابرسی دیتابیس و فایل لنگر کاملاً منطبق و معتبر هستند."),
        EMPTY_UNINITIALIZED("هیچ رکورد حسابرسی هنوز ثبت نشده است."),
        RECOVERY_PENDING_COMMITTED("تراکنش دیتابیس موفق بوده اما انتشار فایل لنگر ناتمام مانده است؛ آماده بازیابی خودکار."),
        RECOVERY_PENDING_ROLLED_BACK("تراکنش دیتابیس لغو شده اما فایل ژورنال موقت باقی مانده است؛ آماده آرشیو شواهد و بازیابی."),
        CORRUPTED_TAMPERED("عدم تطابق رمزنگاری یا دست‌کاری در دیتابیس/فایل لنگر؛ شواهد حفظ شده و نیازمند بررسی امنیتی است.");

        private final String description;

        AuditChainState(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * نقاط تزریق مرگ ناگهانی پروسه (صرفاً جهت تست‌های Crash Recovery در پروسه‌های فرزند).
     */
    public enum CrashPoint {
        NONE,
        AFTER_PENDING_WRITE_BEFORE_DB_SAVE,
        AFTER_DB_COMMIT_BEFORE_ANCHOR_PUBLISH,
        AFTER_ANCHOR_PUBLISH_BEFORE_PENDING_DELETE
    }

    private final Path anchorFilePath;
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository,
                        @Value("${clinic.audit.anchor-path:data/audit_head.anchor}") String anchorPath) {
        this.auditLogRepository = auditLogRepository;
        this.anchorFilePath = Paths.get(anchorPath).toAbsolutePath().normalize();
    }

    @Transactional
    public AuditLog logCurrentActorAction(String defaultRole, String action, String status, String details) {
        String actorUsername = "SYSTEM";
        String actorRole = (defaultRole != null && !defaultRole.isBlank()) ? defaultRole.trim() : "STAFF";
        String clientIp = "127.0.0.1";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            actorUsername = auth.getName();
            actorRole = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(r -> r.replace("ROLE_", ""))
                    .findFirst()
                    .orElse(actorRole);
        }

        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            if (request != null && request.getRemoteAddr() != null && !request.getRemoteAddr().isBlank()) {
                clientIp = request.getRemoteAddr();
            }
        }

        return logAction(actorUsername, actorRole, action, status, clientIp, details);
    }

    @Transactional
    public AuditLog logAction(String actorUsername, String actorRole, String action,
                              String status, String terminalOrIp, String details) {
        AppendState state = (AppendState) TransactionSynchronizationManager.getResource(this);
        boolean firstAppend = (state == null);
        if (firstAppend) {
            state = acquireAppendLock();
        }
        final AppendState appendState = state;
        boolean transactional = TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive();

        try {
            if (firstAppend) {
                if (Files.exists(pendingPath())) {
                    throw new IllegalStateException("Audit recovery required: pending anchor exists; evidence preserved.");
                }
                List<AuditLog> committed = getAuditTrail();
                if (committed.isEmpty() ? Files.exists(anchorFilePath) : !verifyChainIntegrity()) {
                    throw new IllegalStateException("Audit integrity mismatch; existing evidence will not be overwritten.");
                }
                appendState.previous = Files.exists(anchorFilePath)
                        ? Files.readString(anchorFilePath, StandardCharsets.UTF_8).trim()
                        : "EMPTY";

                if (transactional) {
                    TransactionSynchronizationManager.bindResource(this, appendState);
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void suspend() {
                            TransactionSynchronizationManager.unbindResource(AuditService.this);
                        }

                        @Override
                        public void resume() {
                            TransactionSynchronizationManager.bindResource(AuditService.this, appendState);
                        }

                        @Override
                        public void afterCommit() {
                            triggerCrashPointIfConfigured(CrashPoint.AFTER_DB_COMMIT_BEFORE_ANCHOR_PUBLISH);
                            publishPending(appendState);
                        }

                        @Override
                        public void afterCompletion(int completionStatus) {
                            try {
                                if (completionStatus == STATUS_ROLLED_BACK) {
                                    Files.deleteIfExists(pendingPath());
                                }
                            } catch (IOException ex) {
                                throw new IllegalStateException("Audit rollback journal cleanup failed; recovery required.", ex);
                            } finally {
                                TransactionSynchronizationManager.unbindResource(AuditService.this);
                                appendState.close();
                            }
                        }
                    });
                }
            }

            Optional<AuditLog> lastLogOpt = auditLogRepository.findTopByOrderBySequenceNumberDesc();
            long nextSequence = lastLogOpt.map(log -> log.getSequenceNumber() + 1).orElse(1L);
            String prevHash = lastLogOpt.map(AuditLog::getEntryHash).orElse(GENESIS_PREVIOUS_HASH);

            String timestamp = LocalDateTime.now().format(AuditLog.TIMESTAMP_FORMATTER);
            String safeUser = (actorUsername != null && !actorUsername.isBlank()) ? actorUsername.trim() : "SYSTEM";
            String safeRole = (actorRole != null && !actorRole.isBlank()) ? actorRole.trim() : "CORE";
            String safeAction = (action != null && !action.isBlank()) ? action.trim() : "UNSPECIFIED_ACTION";
            String safeStatus = (status != null && !status.isBlank()) ? status.trim() : "SUCCESS";
            String safeIp = (terminalOrIp != null && !terminalOrIp.isBlank()) ? terminalOrIp.trim() : "127.0.0.1";
            String safeDetails = (details != null) ? details.trim() : "";

            String payloadToHash = buildFullPayload(
                    prevHash, nextSequence, timestamp, safeUser, safeRole, safeAction, safeStatus, safeIp, safeDetails
            );
            String calculatedHash = calculateSha256(payloadToHash);

            AuditLog log = new AuditLog(
                    nextSequence,
                    timestamp,
                    safeUser,
                    safeRole,
                    safeAction,
                    safeStatus,
                    prevHash,
                    calculatedHash,
                    safeIp,
                    safeDetails
            );

            appendState.next = signedAnchor(log);
            writePending(appendState);
            triggerCrashPointIfConfigured(CrashPoint.AFTER_PENDING_WRITE_BEFORE_DB_SAVE);

            AuditLog saved = auditLogRepository.save(log);
            auditLogRepository.flush();

            if (!transactional) {
                triggerCrashPointIfConfigured(CrashPoint.AFTER_DB_COMMIT_BEFORE_ANCHOR_PUBLISH);
                publishPending(appendState);
            }
            return saved;
        } catch (IOException ex) {
            throw new IllegalStateException("Audit journal I/O failed; evidence preserved.", ex);
        } finally {
            if (!transactional || !TransactionSynchronizationManager.hasResource(this)) {
                appendState.close();
            }
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditTrail() {
        return auditLogRepository.findAllByOrderBySequenceNumberAsc();
    }

    @Transactional(readOnly = true)
    public boolean verifyChainIntegrity() {
        List<AuditLog> logs = getAuditTrail();
        return !Files.exists(pendingPath())
                && !logs.isEmpty()
                && verifyDatabaseChain(logs)
                && verifyExternalAnchor(logs.get(logs.size() - 1));
    }

    /**
     * تشخیص دقیق وضعیت همگام‌سازی دیتابیس و فایل لنگر بدون تغییر یا حذف شواهد.
     */
    @Transactional(readOnly = true)
    public AuditChainState diagnoseChainState() {
        List<AuditLog> logs = getAuditTrail();
        boolean dbChainValid = logs.isEmpty() || verifyDatabaseChain(logs);
        if (!dbChainValid) {
            return AuditChainState.CORRUPTED_TAMPERED;
        }

        if (Files.exists(pendingPath())) {
            try {
                String[] lines = Files.readString(pendingPath(), StandardCharsets.UTF_8).split("\n", -1);
                if (lines.length != 3 || !constantTimeEquals(lines[2], computeAnchorHmac(lines[0] + "\n" + lines[1]))) {
                    return AuditChainState.CORRUPTED_TAMPERED;
                }
                String current = Files.exists(anchorFilePath) ? Files.readString(anchorFilePath, StandardCharsets.UTF_8).trim() : "EMPTY";
                if (!constantTimeEquals(current, lines[0]) && !constantTimeEquals(current, lines[1])) {
                    return AuditChainState.CORRUPTED_TAMPERED;
                }
                String head = logs.isEmpty() ? "EMPTY" : signedAnchor(logs.get(logs.size() - 1));
                if (constantTimeEquals(head, lines[1])) {
                    return AuditChainState.RECOVERY_PENDING_COMMITTED;
                }
                if (constantTimeEquals(head, lines[0]) && constantTimeEquals(current, lines[0])) {
                    return AuditChainState.RECOVERY_PENDING_ROLLED_BACK;
                }
                return AuditChainState.CORRUPTED_TAMPERED;
            } catch (IOException e) {
                return AuditChainState.CORRUPTED_TAMPERED;
            }
        }

        if (logs.isEmpty()) {
            return Files.exists(anchorFilePath) ? AuditChainState.CORRUPTED_TAMPERED : AuditChainState.EMPTY_UNINITIALIZED;
        }

        return verifyExternalAnchor(logs.get(logs.size() - 1))
                ? AuditChainState.INTACT
                : AuditChainState.CORRUPTED_TAMPERED;
    }

    private boolean verifyDatabaseChain(List<AuditLog> logs) {
        if (logs.isEmpty()) {
            return false;
        }

        String expectedPrev = GENESIS_PREVIOUS_HASH;
        long expectedSequence = 1L;

        for (AuditLog log : logs) {
            if (log.getSequenceNumber() != expectedSequence) {
                return false;
            }
            if (!constantTimeEquals(log.getPreviousHash(), expectedPrev)) {
                return false;
            }

            String fullPayload = buildFullPayload(
                    log.getPreviousHash(),
                    log.getSequenceNumber(),
                    log.getTimestamp(),
                    log.getActorUsername(),
                    log.getActorRole(),
                    log.getAction(),
                    log.getStatus(),
                    log.getTerminalOrIp(),
                    log.getDetails()
            );
            String recomputedHash = calculateSha256(fullPayload);

            if (!constantTimeEquals(recomputedHash, log.getEntryHash())) {
                return false;
            }

            expectedPrev = log.getEntryHash();
            expectedSequence++;
        }

        return true;
    }

    // ==================== Anchor Persistence, Locking & Recovery ====================

    private Path pendingPath() {
        return anchorFilePath.resolveSibling(anchorFilePath.getFileName() + ".pending");
    }

    private String signedAnchor(AuditLog log) {
        String payload = log.getSequenceNumber() + ":" + log.getEntryHash();
        return payload + ":" + computeAnchorHmac(payload);
    }

    private AppendState acquireAppendLock() {
        long timeoutMs = Long.getLong("clinic.audit.lock-timeout-ms", 0L);
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMs));

        ReentrantLock jvmLock = IN_PROCESS_LOCKS.computeIfAbsent(anchorFilePath, p -> new ReentrantLock());
        boolean jvmLocked = false;
        try {
            if (timeoutMs > 0) {
                jvmLocked = jvmLock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                jvmLocked = jvmLock.tryLock();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Audit writer interrupted while acquiring lock.", ie);
        }

        if (!jvmLocked) {
            throw new IllegalStateException("Audit writer busy; retry the entire transaction.");
        }

        FileChannel channel = null;
        try {
            Path parent = anchorFilePath.getParent();
            Files.createDirectories(parent);
            Path lockFile = anchorFilePath.resolveSibling(anchorFilePath.getFileName() + ".lock");
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            FileLock fileLock = null;
            while (true) {
                try {
                    fileLock = channel.tryLock();
                } catch (OverlappingFileLockException ignored) {
                    fileLock = null;
                }
                if (fileLock != null) {
                    return new AppendState(jvmLock, channel, fileLock);
                }
                if (System.nanoTime() >= deadlineNanos) {
                    throw new IllegalStateException("Audit writer busy; retry the entire transaction.");
                }
                Thread.sleep(15L);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            closeChannelQuietly(channel);
            jvmLock.unlock();
            throw new IllegalStateException("Audit writer interrupted while waiting for OS file lock.", ie);
        } catch (IOException | IllegalStateException ex) {
            closeChannelQuietly(channel);
            jvmLock.unlock();
            throw new IllegalStateException("Audit writer lock unavailable; retry the entire transaction.", ex);
        }
    }

    private static void closeChannelQuietly(FileChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void writePending(AppendState state) throws IOException {
        String payload = state.previous + "\n" + state.next;
        atomicWrite(pendingPath(), payload + "\n" + computeAnchorHmac(payload));
    }

    private void atomicWrite(Path destination, String content) throws IOException {
        Path parent = destination.toAbsolutePath().getParent();
        Path temporary = Files.createTempFile(parent, "audit-", ".tmp");
        try (FileChannel tempChannel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            tempChannel.write(StandardCharsets.UTF_8.encode(content));
            tempChannel.force(true);
        }
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void publishPending(AppendState state) {
        try {
            atomicWrite(anchorFilePath, state.next);
            triggerCrashPointIfConfigured(CrashPoint.AFTER_ANCHOR_PUBLISH_BEFORE_PENDING_DELETE);
            Files.delete(pendingPath());
        } catch (IOException ex) {
            throw new IllegalStateException("Database may already be committed; audit pending evidence retained. Recover before retrying.", ex);
        }
    }

    private static void triggerCrashPointIfConfigured(CrashPoint point) {
        String configured = System.getProperty("clinic.audit.crash-point", "NONE").trim();
        if (point.name().equalsIgnoreCase(configured)) {
            // توقف آنی پروسه در سطح سیستم‌عامل (مشابه قطع برق یا kill -9) بدون اجرای finally یا Shutdown Hook
            Runtime.getRuntime().halt(86);
        }
    }

    /**
     * بازیابی صریح و امن در صورت وجود فایل ژورنال .pending پس از کرش یا خطای دیسک.
     * هیچ رکورد تاییدنشده‌ای بدون تطابق امضای دیجیتالِ ژورنال و سلامت کل زنجیره دیتابیس پذیرفته نمی‌شود.
     */
    @Transactional(readOnly = true)
    public void recoverPendingAnchor() {
        try (AppendState state = acquireAppendLock()) {
            String[] lines = Files.readString(pendingPath(), StandardCharsets.UTF_8).split("\n", -1);
            if (lines.length != 3 || !constantTimeEquals(lines[2], computeAnchorHmac(lines[0] + "\n" + lines[1]))) {
                throw new IllegalStateException("Invalid pending evidence; manual investigation required.");
            }
            String current = Files.exists(anchorFilePath) ? Files.readString(anchorFilePath, StandardCharsets.UTF_8).trim() : "EMPTY";
            if (!constantTimeEquals(current, lines[0]) && !constantTimeEquals(current, lines[1])) {
                throw new IllegalStateException("Anchor diverged from pending evidence; manual investigation required.");
            }
            List<AuditLog> logs = getAuditTrail();
            String head = logs.isEmpty() ? "EMPTY" : signedAnchor(logs.get(logs.size() - 1));
            if (!logs.isEmpty() && !verifyDatabaseChain(logs)) {
                throw new IllegalStateException("Database chain corrupted; evidence preserved.");
            }
            if (constantTimeEquals(head, lines[1])) {
                state.next = lines[1];
                publishPending(state);
            } else if (constantTimeEquals(head, lines[0]) && constantTimeEquals(current, lines[0])) {
                Files.move(pendingPath(), pendingPath().resolveSibling(pendingPath().getFileName()
                        + ".rolled-back-" + java.util.UUID.randomUUID()), StandardCopyOption.ATOMIC_MOVE);
            } else {
                throw new IllegalStateException("Database does not match signed pending outcome; evidence preserved.");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Audit recovery I/O failed; evidence preserved.", ex);
        }
    }

    private static class AppendState implements AutoCloseable {
        private final ReentrantLock jvmLock;
        private final FileChannel channel;
        private final FileLock lock;
        private String previous;
        private String next;
        private boolean closed = false;

        AppendState(ReentrantLock jvmLock, FileChannel channel, FileLock lock) {
            this.jvmLock = jvmLock;
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (lock != null && lock.isValid()) {
                    lock.release();
                }
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Audit lock release failed.", ex);
            } finally {
                if (jvmLock != null && jvmLock.isHeldByCurrentThread()) {
                    jvmLock.unlock();
                }
            }
        }
    }

    private boolean verifyExternalAnchor(AuditLog latestLog) {
        if (!Files.exists(anchorFilePath)) {
            return false;
        }
        try {
            String content = Files.readString(anchorFilePath, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                return false;
            }

            String expectedPayload = latestLog.getSequenceNumber() + ":" + latestLog.getEntryHash();
            String expectedSignature = computeAnchorHmac(expectedPayload);
            String expectedFullRecord = expectedPayload + ":" + expectedSignature;

            return constantTimeEquals(content, expectedFullRecord);
        } catch (IOException e) {
            return false;
        }
    }

    private String buildFullPayload(String prevHash, long seq, String timestamp, String user,
                                    String role, String action, String status, String ip, String details) {
        return String.join(":",
                prevHash,
                String.valueOf(seq),
                timestamp,
                user,
                role,
                action,
                status,
                ip,
                (details != null ? details : "")
        );
    }

    private static String computeAnchorHmac(String data) {
        try {
            byte[] keyBytes = HmacKeyProvider.getAuditKey();
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("خطا در تولید امضای HMAC لنگر حسابرسی.", e);
        }
    }

    public static String calculateSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("الگوریتم SHA-256 در دسترس نیست.", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.toLowerCase().getBytes(StandardCharsets.UTF_8),
                b.toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }
}