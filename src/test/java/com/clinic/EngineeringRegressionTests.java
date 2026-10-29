package com.clinic;

import com.clinic.config.DataInitializer;
import com.clinic.enums.PatientStatus;
import com.clinic.models.*;
import com.clinic.repository.*;
import com.clinic.service.*;
import com.clinic.system.SystemClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EngineeringRegressionTests {
    @TempDir
    Path temporaryDirectory;

    private AuditService isolatedAuditService() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.findTopByOrderBySequenceNumberDesc()).thenReturn(Optional.empty());
        when(repository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new AuditService(repository,
                temporaryDirectory.resolve("isolated.anchor").toString());
    }

    @Test
    void unsignedAuditAnchorMustBeRejected() throws Exception {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.findTopByOrderBySequenceNumberDesc()).thenReturn(Optional.empty());
        when(repository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Path anchor = temporaryDirectory.resolve("audit.anchor");
        AuditService service = new AuditService(repository, anchor.toString());
        AuditLog log = service.logAction("SYSTEM", "CORE", "GENESIS", "SUCCESS", "127.0.0.1", "test");
        when(repository.findAllByOrderBySequenceNumberAsc()).thenReturn(List.of(log));
        assertTrue(service.verifyChainIntegrity());
        Files.writeString(anchor, log.getSequenceNumber() + ":" + log.getEntryHash());
        assertFalse(service.verifyChainIntegrity());
    }

    @Test
    void disablingDemoSeedMustOnlyBootstrapAdmin() {
        StaffRepository repository = mock(StaffRepository.class);
        when(repository.findByUsername(anyString())).thenReturn(Optional.empty());
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("encoded");
        AuditService audit = isolatedAuditService();
        DataInitializer initializer = new DataInitializer(repository, encoder, audit);
        ReflectionTestUtils.setField(initializer, "seedDefaultStaff", false);
        ReflectionTestUtils.setField(initializer, "bootstrapAdminPassword", "synthetic-password");
        initializer.run();
        verify(repository, times(1)).save(any(Admin.class));
        verify(repository, never()).findByUsername("doctor");
        verify(repository, never()).findByUsername("cashier");
    }

    @Test
    void concurrentIdempotentReplayMustBeRecheckedAfterPatientLock() {
        FinancialRepository financial = mock(FinancialRepository.class);
        PatientRepository patients = mock(PatientRepository.class);
        BillingService service = new BillingService(financial, patients, isolatedAuditService());
        Cashier cashier = new Cashier("1001", "Cashier", 30, "09120000001", BigDecimal.ZERO, new BigDecimal("1000"));
        Patient patient = new Patient("2001", "Patient", 30, "09120000002", Patient.BloodType.UNKNOWN);
        patient.transitionTo(PatientStatus.WAITING_FOR_PAYMENT);
        patient.transitionTo(PatientStatus.PAYMENT_COMPLETED);
        Receipt receipt = new Receipt("POS-TEST", "2001", "DOC", cashier.getUsername(),
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, Receipt.PaymentMethod.CASH,
                Receipt.ReceiptStatus.PAID, "ordinary description", SystemClock.now(), "key");
        when(financial.findByIdempotencyKey("key")).thenReturn(Optional.empty(), Optional.of(receipt));
        when(patients.findByNationalIdForUpdate("2001")).thenReturn(Optional.of(patient));
        assertSame(receipt, service.processPatientSettlement(cashier, "2001", new BigDecimal("100"),
                BigDecimal.ZERO, BigDecimal.ZERO, Receipt.PaymentMethod.CASH, "description", "key"));
        verify(financial, never()).save(any());
        verify(financial, never()).findByDescriptionContaining(anyString());
    }

    @Test
    void replayOfTamperedReceiptMustFailClosed() {
        FinancialRepository financial = mock(FinancialRepository.class);
        BillingService service = new BillingService(financial, mock(PatientRepository.class), isolatedAuditService());
        Cashier cashier = new Cashier("1001", "Cashier", 30, "09120000001", BigDecimal.ZERO, new BigDecimal("1000"));
        Receipt receipt = new Receipt("POS-TEST", "2001", "DOC", cashier.getUsername(),
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, Receipt.PaymentMethod.CASH,
                Receipt.ReceiptStatus.PAID, "test", SystemClock.now(), "key");
        ReflectionTestUtils.setField(receipt, "integrityHash", "tampered");
        when(financial.findByIdempotencyKey("key")).thenReturn(Optional.of(receipt));
        assertThrows(SecurityException.class, () -> service.processPatientSettlement(cashier, "2001",
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, Receipt.PaymentMethod.CASH, "test", "key"));
    }

    @Test
    void triageMustRejectNaNTemperature() {
        Nurse nurse = new Nurse("1001", "Nurse", 30, "09120000001", BigDecimal.ZERO, "NRS-1", "Triage");
        Patient patient = new Patient("2001", "Patient", 30, "09120000002", Patient.BloodType.UNKNOWN);
        assertThrows(IllegalArgumentException.class,
                () -> nurse.performTriage(patient, "120/80", Double.NaN, 80, "test", "test"));
    }

    @Test
    void discountedReceiptCannotRefundMoreThanNetCollected() {
        Receipt receipt = new Receipt("POS-TEST", "2001", "DOC", "cashier", new BigDecimal("100"),
                new BigDecimal("10"), new BigDecimal("50"), Receipt.PaymentMethod.CASH,
                Receipt.ReceiptStatus.PAID, "test", SystemClock.now());
        assertEquals(0, new BigDecimal("60").compareTo(receipt.getRemainingRefundableAmount()));
        assertThrows(IllegalArgumentException.class, () -> receipt.applyRefund(new BigDecimal("60.01"), "test"));
        receipt.applyRefund(new BigDecimal("20"), "partial");
        assertEquals(0, new BigDecimal("40").compareTo(receipt.getRemainingRefundableAmount()));
        receipt.applyRefund(new BigDecimal("40"), "remaining");
        assertEquals(Receipt.ReceiptStatus.REFUNDED, receipt.getReceiptStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(receipt.getNetRetainedAmount()));
        assertTrue(receipt.verifyIntegrity());
    }

    @Test
    void financialSummaryMustDefensivelyCopyPaymentBreakdown() {
        var methods = new java.util.LinkedHashMap<String, BigDecimal>();
        methods.put("CASH", new BigDecimal("100"));
        var summary = new com.clinic.dto.ClinicalDTOs.FinancialSummaryResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L, true, methods);
        methods.put("CASH", BigDecimal.ZERO);
        assertEquals(new BigDecimal("100"), summary.revenueByPaymentMethod().get("CASH"));
        assertThrows(UnsupportedOperationException.class,
                () -> summary.revenueByPaymentMethod().put("CASH", BigDecimal.ZERO));
    }

    @Test
    void suspendedAccountMustInvalidateExistingSession() throws Exception {
        assertSessionRevoked(false, "NURSE");
    }

    @Test
    void changedRoleMustInvalidateExistingSession() throws Exception {
        assertSessionRevoked(true, "DOCTOR");
    }

    private void assertSessionRevoked(boolean active, String cachedRole) throws Exception {
        StaffRepository repository = mock(StaffRepository.class);
        Nurse nurse = new Nurse("1001", "Nurse", 30, "09120000001", BigDecimal.ZERO, "NRS-1", "Triage");
        nurse.setActive(active);
        when(repository.findByUsername("nurse")).thenReturn(Optional.of(nurse));
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        request.setSession(session);
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("nurse", "unused",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(cachedRole))));
        try {
            org.springframework.mock.web.MockFilterChain chain = new org.springframework.mock.web.MockFilterChain();
            new com.clinic.security.ActiveStaffSessionFilter(repository).doFilter(request, response, chain);
            assertEquals(401, response.getStatus());
            assertTrue(session.isInvalid());
            assertNull(chain.getRequest());
            assertNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}