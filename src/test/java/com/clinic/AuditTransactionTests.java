package com.clinic;

import com.clinic.repository.AuditLogRepository;
import com.clinic.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuditTransactionTests {
    @Autowired AuditLogRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    @TempDir Path directory;
    private AuditService audit;
    private TransactionTemplate transaction;
    private Path anchor;

    @BeforeEach
    void initializeDisposableChain() {
        anchor = directory.resolve("head.anchor");
        audit = new AuditService(repository, anchor.toString());
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> append("GENESIS"));
    }

    private void append(String action) {
        audit.logAction("synthetic", "CORE", action, "SUCCESS", "127.0.0.1", "test");
    }

    @Test
    void rollbackMustLeaveCommittedAnchorUnchanged() throws Exception {
        String before = Files.readString(anchor);
        transaction.executeWithoutResult(status -> {
            append("ROLLED_BACK");
            status.setRollbackOnly();
        });
        assertEquals(1, repository.count());
        assertEquals(before, Files.readString(anchor));
        assertTrue(audit.verifyChainIntegrity());
    }

    @Test
    void appendMustNotOverwriteMismatchedEvidence() throws Exception {
        Files.writeString(anchor, "synthetic-corrupt-evidence");
        assertThrows(IllegalStateException.class,
                () -> transaction.executeWithoutResult(status -> append("MUST_NOT_HIDE_CORRUPTION")));
        assertEquals("synthetic-corrupt-evidence", Files.readString(anchor));
        assertEquals(1, repository.count());
    }

    @Test
    void committedPublicationFailureMustBeRecoverableWithoutDuplicatingLog() throws Exception {
        String before = Files.readString(anchor);
        Path preserved = directory.resolve("preserved.anchor");
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            append("COMMITTED");
            try {
                Files.move(anchor, preserved);
                Files.createDirectory(anchor); // deterministic publication failure, not OS permission assumptions
            } catch (java.io.IOException ex) { throw new RuntimeException(ex); }
        }));
        assertEquals(2, repository.count(), "afterCommit failure cannot roll back committed DB work");
        assertTrue(Files.exists(directory.resolve("head.anchor.pending")));
        assertEquals(before, Files.readString(preserved));
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> append("RETRY")));
        Files.delete(anchor); // disposable empty obstruction only
        Files.move(preserved, anchor);
        AuditService restarted = new AuditService(repository, anchor.toString());
        transaction.executeWithoutResult(status -> restarted.recoverPendingAnchor());
        assertEquals(2, repository.count());
        assertTrue(audit.verifyChainIntegrity());
    }

    @Test
    void pendingIoFailureMustRollBackDatabaseAndPreserveHead() throws Exception {
        String before = Files.readString(anchor);
        Path pending = directory.resolve("head.anchor.pending");
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            // Simulates destination becoming unavailable after admission but before staging.
            append("FIRST");
            try {
                Files.delete(pending);
                Files.createDirectory(pending);
            } catch (java.io.IOException ex) { throw new RuntimeException(ex); }
            append("SECOND");
        }));
        assertEquals(1, repository.count());
        assertEquals(before, Files.readString(anchor));
        assertTrue(audit.verifyChainIntegrity());
    }

    @Test
    void concurrentWriterMustNotPassBeforeOuterTransactionCompletes() throws Exception {
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            transaction.executeWithoutResult(status -> {
                append("FIRST");
                var second = executor.submit(() -> assertThrows(IllegalStateException.class,
                        () -> transaction.executeWithoutResult(other -> append("BUSY"))));
                try { second.get(5, java.util.concurrent.TimeUnit.SECONDS); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            });
            transaction.executeWithoutResult(status -> append("RETRY_AFTER_COMMIT"));
            assertEquals(3, repository.count());
            assertTrue(audit.verifyChainIntegrity());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void interruptedUncommittedJournalMustBeArchivedNotPromoted() throws Exception {
        String before = Files.readString(anchor);
        Path pending = directory.resolve("head.anchor.pending");
        String[] journal = new String[1];
        transaction.executeWithoutResult(status -> {
            append("INTERRUPTED_BEFORE_COMMIT");
            try { journal[0] = Files.readString(pending); }
            catch (java.io.IOException ex) { throw new RuntimeException(ex); }
            status.setRollbackOnly();
        });
        // Recreate the exact persisted journal left if the process died before rollback callbacks.
        Files.writeString(pending, journal[0]);
        AuditService restarted = new AuditService(repository, anchor.toString());
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status ->
                restarted.logAction("synthetic", "CORE", "RETRY", "SUCCESS", "127.0.0.1", "test")));
        transaction.executeWithoutResult(status -> restarted.recoverPendingAnchor());
        assertEquals(before, Files.readString(anchor));
        assertEquals(1, repository.count());
        try (var files = Files.list(directory)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().contains(".rolled-back-")));
        }
        assertTrue(restarted.verifyChainIntegrity());
    }

    @Test
    void tamperedPendingRecoveryMustPreserveAllEvidence() throws Exception {
        String before = Files.readString(anchor);
        Path pending = directory.resolve("head.anchor.pending");
        Files.writeString(pending, "invalid-journal");
        assertThrows(IllegalStateException.class,
                () -> transaction.executeWithoutResult(status -> audit.recoverPendingAnchor()));
        assertEquals("invalid-journal", Files.readString(pending));
        assertEquals(before, Files.readString(anchor));
        assertFalse(audit.verifyChainIntegrity());
    }

    @Test
    void databaseInsertFailureMustNotPublishPreparedHead() throws Exception {
        String before = Files.readString(anchor);
        assertThrows(RuntimeException.class, () -> transaction.executeWithoutResult(status ->
                audit.logAction("synthetic", "CORE", "INVALID_INSERT", "SUCCESS", "127.0.0.1", "x".repeat(1001))));
        assertEquals(1, repository.count());
        assertEquals(before, Files.readString(anchor));
        assertFalse(Files.exists(directory.resolve("head.anchor.pending")));
        transaction.executeWithoutResult(status -> append("RETRY_WITH_VALID_DATA"));
        assertEquals(2, repository.count());
        assertTrue(audit.verifyChainIntegrity());
    }

    @Test
    void multipleAppendsMustPublishOnlyFinalCommittedHead() throws Exception {
        String before = Files.readString(anchor);
        transaction.executeWithoutResult(status -> {
            append("ONE");
            append("TWO");
            try { assertEquals(before, Files.readString(anchor)); }
            catch (java.io.IOException ex) { throw new RuntimeException(ex); }
        });
        assertEquals(3, repository.count());
        assertTrue(Files.readString(anchor).startsWith("3:"));
        assertTrue(audit.verifyChainIntegrity());
    }

    @Test
    void requiresNewMustNotReuseSuspendedOuterJournal() {
        TransactionTemplate inner = new TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            append("OUTER");
            assertThrows(IllegalStateException.class, () -> inner.executeWithoutResult(other -> append("INNER")));
            append("OUTER_CONTINUES");
        });
        assertEquals(3, repository.count());
        assertTrue(audit.verifyChainIntegrity());
    }

    @Test
    void interruptionAfterPublicationBeforeJournalCleanupMustRecoverIdempotently() throws Exception {
        Path pending = directory.resolve("head.anchor.pending");
        String[] journal = new String[1];
        transaction.executeWithoutResult(status -> {
            append("COMMITTED");
            try { journal[0] = Files.readString(pending); }
            catch (java.io.IOException ex) { throw new RuntimeException(ex); }
        });
        String committedHead = Files.readString(anchor);
        Files.writeString(pending, journal[0]);
        AuditService restarted = new AuditService(repository, anchor.toString());
        transaction.executeWithoutResult(status -> restarted.recoverPendingAnchor());
        assertEquals(committedHead, Files.readString(anchor));
        assertEquals(2, repository.count());
        assertFalse(Files.exists(pending));
        assertTrue(restarted.verifyChainIntegrity());
    }

    @Test
    void failureDuringCommitPreparationMustLeaveHeadUnchanged() throws Exception {
        String before = Files.readString(anchor);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            append("COMMIT_REJECTED");
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void beforeCommit(boolean readOnly) {
                            throw new IllegalStateException("synthetic commit preparation failure");
                        }
                    });
        }));
        assertEquals(1, repository.count());
        assertEquals(before, Files.readString(anchor));
        assertTrue(audit.verifyChainIntegrity());
    }
}