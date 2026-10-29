package com.clinic.controller;

import com.clinic.models.AuditLog;
import com.clinic.service.AuditService;
import com.clinic.service.AuditService.AuditChainState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> getAuditTrail() {
        return ResponseEntity.ok(auditService.getAuditTrail());
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyIntegrity() {
        List<AuditLog> logs = auditService.getAuditTrail();
        boolean isIntact = auditService.verifyChainIntegrity();
        AuditChainState chainState = auditService.diagnoseChainState();
        String lastHash = logs.isEmpty() ? AuditService.GENESIS_PREVIOUS_HASH : logs.get(logs.size() - 1).getEntryHash();

        return ResponseEntity.ok(Map.of(
                "intact", isIntact,
                "chainState", chainState.name(),
                "recoverable", (chainState == AuditChainState.RECOVERY_PENDING_COMMITTED
                        || chainState == AuditChainState.RECOVERY_PENDING_ROLLED_BACK),
                "totalBlocks", logs.size(),
                "algorithm", "SHA-256 Merkle Chain + HMAC-SHA256 Anchor",
                "latestHash", lastHash,
                "statusMessage", chainState.getDescription()
        ));
    }

    @PostMapping("/recover")
    public ResponseEntity<Map<String, Object>> recoverPendingJournal() {
        auditService.recoverPendingAnchor();
        AuditChainState stateAfter = auditService.diagnoseChainState();
        return ResponseEntity.ok(Map.of(
                "recovered", auditService.verifyChainIntegrity(),
                "chainState", stateAfter.name(),
                "statusMessage", stateAfter.getDescription()
        ));
    }
}