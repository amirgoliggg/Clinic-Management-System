package service;

import models.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.AuditLogRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

@Service
public class AuditService {

    public static final String GENESIS_PREVIOUS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public synchronized AuditLog logAction(String actorUsername, String actorRole, String action,
                                           String status, String terminalOrIp, String details) {
        Optional<AuditLog> lastLogOpt = auditLogRepository.findTopByOrderBySequenceNumberDesc();

        long nextSequence = lastLogOpt.map(log -> log.getSequenceNumber() + 1).orElse(1L);
        String prevHash = lastLogOpt.map(AuditLog::getEntryHash).orElse(GENESIS_PREVIOUS_HASH);

        String payloadToHash = prevHash + ":" + nextSequence + ":" + actorUsername + ":" + action + ":" + status + ":" + details;
        String calculatedHash = calculateSha256(payloadToHash);

        AuditLog log = new AuditLog(
                nextSequence,
                actorUsername != null ? actorUsername : "SYSTEM",
                actorRole != null ? actorRole : "CORE",
                action,
                status != null ? status : "SUCCESS",
                prevHash,
                calculatedHash,
                terminalOrIp != null ? terminalOrIp : "127.0.0.1",
                details != null ? details : ""
        );

        return auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditTrail() {
        return auditLogRepository.findAllByOrderBySequenceNumberAsc();
    }

    @Transactional(readOnly = true)
    public boolean verifyChainIntegrity() {
        List<AuditLog> logs = getAuditTrail();
        if (logs.isEmpty()) return true;

        String expectedPrev = GENESIS_PREVIOUS_HASH;
        for (AuditLog log : logs) {
            if (!log.getPreviousHash().equalsIgnoreCase(expectedPrev)) {
                return false;
            }
            String payload = log.getPreviousHash() + ":" + log.getSequenceNumber() + ":" +
                    log.getActorUsername() + ":" + log.getAction() + ":" +
                    log.getStatus() + ":" + log.getDetails();
            String recomputedHash = calculateSha256(payload);
            if (!recomputedHash.equalsIgnoreCase(log.getEntryHash())) {
                return false;
            }
            expectedPrev = log.getEntryHash();
        }
        return true;
    }

    public static String calculateSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("الگوریتم SHA-256 در دسترس نیست", e);
        }
    }
}