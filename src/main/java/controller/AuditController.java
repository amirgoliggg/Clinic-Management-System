package controller;

import models.AuditLog;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.AuditService;

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
        String lastHash = logs.isEmpty() ? AuditService.GENESIS_PREVIOUS_HASH : logs.get(logs.size() - 1).getEntryHash();

        return ResponseEntity.ok(Map.of(
                "intact", isIntact,
                "totalBlocks", logs.size(),
                "algorithm", "SHA-256 Merkle Chain",
                "latestHash", lastHash,
                "statusMessage", isIntact
                        ? "صحت ریاضی ۱۰۰٪ تایید شد. تمامی هش‌های متوالی همخوانی دارند و هیچ دستکاری یا حذفی در داده‌ها رخ نداده است."
                        : "هشدار امنیتی بسیار مهم: زنجیره هش‌ها شکسته شده یا رکوردی در دیتابیس دستکاری شده است!"
        ));
    }
}