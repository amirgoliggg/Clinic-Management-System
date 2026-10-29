package com.clinic;

import com.clinic.ClinicApplication;
import com.clinic.dto.ClinicalDTOs.FinancialSummaryResponse;
import com.clinic.enums.PatientStatus;
import com.clinic.models.Admin;
import com.clinic.models.Patient;
import com.clinic.models.Receipt;
import com.clinic.repository.AuditLogRepository;
import com.clinic.repository.FinancialRepository;
import com.clinic.repository.PatientRepository;
import com.clinic.service.AuditService;
import com.clinic.service.BillingService;
import com.clinic.system.SystemClock;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ClinicApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testclinicdb;DB_CLOSE_DELAY=-1",
        "clinic.browser.auto-open=false",
        "server.ssl.enabled=false"
})
public class ClinicSystemIntegrationTests {

    @DynamicPropertySource
    static void isolatedAuditAnchor(DynamicPropertyRegistry registry) {
        registry.add("clinic.audit.anchor-path", () -> System.getProperty("java.io.tmpdir")
                + "/clinic-test-" + java.util.UUID.randomUUID() + ".anchor");
    }

    // Valid CSRF tokens let workflow/RBAC tests reach the intended application guard.
    private static MockHttpServletRequestBuilder post(String path) {
        return MockMvcRequestBuilders.post(path)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf());
    }

    private static MockHttpServletRequestBuilder put(String path) {
        return MockMvcRequestBuilders.put(path)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf());
    }

    @Test
    @WithMockUser(username = "receptionist", authorities = "RECEPTIONIST")
    void stateChangingRequestWithoutCsrfShouldBeRejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/clinical/patients/register")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfEndpointShouldSupplySessionToken() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
    }

    @Test
    void loginWithRealSessionCsrfTokenShouldAuthenticate() throws Exception {
        var tokenResult = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) tokenResult.getRequest().getSession(false);
        var token = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        var loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"doctor\",\"password\":\"Doctor@123\"}"))
                .andExpect(status().isOk()).andReturn();
        var authenticatedSession = (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession(false);
        mockMvc.perform(get("/api/auth/me").session(authenticatedSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("doctor"));
    }

    @Test
    void disabledStaffWithCachedAuthenticationShouldBeRejectedByFilterChain() throws Exception {
        var nurse = new com.clinic.models.Nurse("SUSP-TEST-1001", "Suspended Nurse", 30, "09120000001",
                BigDecimal.ZERO, "NRS-SUSP-1001", "Triage");
        nurse.setUsername("suspended-test-nurse");
        nurse.setActive(false);
        staffRepository.save(nurse);
        mockMvc.perform(get("/api/clinical/patients/status/WAITING_FOR_DOCTOR")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("suspended-test-nurse").authorities(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("NURSE"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("SESSION_REVOKED"));
    }

    @Test
    @WithMockUser(username = "nurse", authorities = "NURSE")
    void triageMustRejectMalformedBloodPressure() throws Exception {
        mockMvc.perform(post("/api/clinical/records/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientNationalId\":\"1111111111\",\"bloodPressure\":\"invalid\",\"bodyTemperatureCelsius\":37,\"heartRateBpm\":80}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "nurse", authorities = "NURSE")
    void triageMustRejectMissingVitals() throws Exception {
        mockMvc.perform(post("/api/clinical/records/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientNationalId\":\"1111111111\",\"bloodPressure\":\"120/80\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private FinancialRepository financialRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private BillingService billingService;

    @Autowired
    private com.clinic.repository.StaffRepository staffRepository;

    @BeforeEach
    void ensureAuditChainGenesis() {
        // RBAC principal must correspond to a real active account after session revalidation.
        if (!staffRepository.existsByUsername("hr_manager")) {
            staffRepository.save(new Admin("HR-TEST-1001", "HR Manager", 35, "09120000001",
                    BigDecimal.ZERO, "hr_manager", "synthetic-unused-hash", Admin.AdminRole.HR_ADMIN));
        }
        if (auditService.getAuditTrail().isEmpty()) {
            auditService.logAction("SYSTEM", "CORE", "GENESIS_BOOTSTRAP", "SUCCESS", "127.0.0.1", "ایجاد بلوک جنسیس");
        }
    }

    private Patient createTestPatient(String nationalId, String fullName, BigDecimal initialDebt, PatientStatus status) {
        patientRepository.findByNationalId(nationalId).ifPresent(patientRepository::delete);
        Patient patient = new Patient(nationalId, fullName, 32, "09121112233", Patient.BloodType.O_POSITIVE);
        patient.setPendingBill(initialDebt);
        if (status != PatientStatus.WAITING_FOR_DOCTOR) {
            patient.transitionTo(status);
        }
        return patientRepository.save(patient);
    }

    // ==================== ۱. تست‌های امنیت و کنترل دسترسی (RBAC) ====================

    @Test
    @DisplayName("۱. رد درخواست ثبت بیمار بدون احراز هویت")
    void unauthenticatedPatientRegistrationShouldBeRejected() throws Exception {
        String json = """
                {
                    "nationalId": "1111111111",
                    "fullName": "بیمار تستی",
                    "age": 30,
                    "contactNumber": "09121111111",
                    "bloodType": "O_POSITIVE"
                }
                """;

        mockMvc.perform(post("/api/clinical/patients/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("۲. جلوگیری از ثبت ویزیت و نسخه پزشکی توسط پرستار")
    @WithMockUser(username = "nurse", authorities = {"ROLE_NURSE", "NURSE"})
    void nurseAttemptingMedicalConsultationShouldBeForbidden() throws Exception {
        String json = """
                {
                    "patientNationalId": "1111111111",
                    "diagnosis": "سرماخوردگی",
                    "prescription": "استامینوفن",
                    "notes": "تست نفوذ"
                }
                """;

        mockMvc.perform(post("/api/clinical/records/consultation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("۳. جلوگیری از ارتقای دسترسی: مدیر منابع انسانی (HR_ADMIN) نباید بتواند SUPER_ADMIN بسازد")
    @WithMockUser(username = "hr_manager", authorities = {"ROLE_HR_ADMIN", "HR_ADMIN"})
    void hrAdminAttemptingToCreateSuperAdminShouldBeForbidden() throws Exception {
        String escalationJson = """
                {
                    "username": "rogue_admin",
                    "password": "StrongPassword123",
                    "nationalId": "9988776655",
                    "fullName": "نفوذگر تست",
                    "age": 30,
                    "contactNumber": "09120009999",
                    "baseSalary": 50000.00,
                    "role": "SUPER_ADMIN"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(escalationJson))
                .andExpect(status().isForbidden());
    }

    // ==================== ۲. تست‌های گردش کار بالینی (پزشک و پرستار) ====================

    @Test
    @DisplayName("۴. نادیده گرفتن فیلدهای جعلی بدهی و وضعیت هنگام ثبت بیمار جدید")
    @WithMockUser(username = "receptionist", authorities = {"ROLE_RECEPTIONIST", "RECEPTIONIST"})
    void patientRegistrationWithForgedPendingBillShouldIgnoreForgedField() throws Exception {
        patientRepository.findByNationalId("1234567890").ifPresent(patientRepository::delete);

        String maliciousJson = """
                {
                    "nationalId": "1234567890",
                    "fullName": "رضا محمدی",
                    "age": 34,
                    "contactNumber": "09123334444",
                    "bloodType": "A_POSITIVE",
                    "pendingBill": 999999.00,
                    "currentStatus": "PAYMENT_COMPLETED"
                }
                """;

        mockMvc.perform(post("/api/clinical/patients/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingBill").value(0.0))
                .andExpect(jsonPath("$.currentStatus").value("WAITING_FOR_DOCTOR"));

        Patient saved = patientRepository.findByNationalId("1234567890").orElseThrow();
        assertEquals(0, saved.getPendingBill().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("۵. انجام موفق تریاژ و ثبت علائم حیاتی بیمار توسط پرستار")
    @WithMockUser(username = "nurse", authorities = {"ROLE_NURSE", "NURSE"})
    void nurseShouldSuccessfullyPerformPatientTriage() throws Exception {
        createTestPatient("2222333344", "بیمار اورژانسی", BigDecimal.ZERO, PatientStatus.WAITING_FOR_DOCTOR);

        String triageJson = """
                {
                    "patientNationalId": "2222333344",
                    "bloodPressure": "120/80",
                    "bodyTemperatureCelsius": 37.4,
                    "heartRateBpm": 78,
                    "chiefComplaint": "سرگیجه و ضعف عمومی",
                    "nursingNotes": "علائم حیاتی پایدار است"
                }
                """;

        mockMvc.perform(post("/api/clinical/records/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(triageJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientNationalId").value("2222333344"));
    }

    @Test
    @DisplayName("۶. ثبت ویزیت پزشک، افزودن بدهی و جلوگیری از لغو پذیرش بیمار دارای بدهی")
    @WithMockUser(username = "doctor", authorities = {"ROLE_DOCTOR", "DOCTOR"})
    void consultationShouldAddChargeAndPreventCancellationWithOpenDebt() throws Exception {
        createTestPatient("3333444455", "احمد کریمی", BigDecimal.ZERO, PatientStatus.WAITING_FOR_DOCTOR);

        String consultationJson = """
                {
                    "patientNationalId": "3333444455",
                    "diagnosis": "آنفولانزای فصلی",
                    "prescription": "آموکسی‌سیلین و استراحت",
                    "notes": "تب ۳۸.۵ درجه"
                }
                """;

        mockMvc.perform(post("/api/clinical/records/consultation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultationJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("آنفولانزای فصلی"));

        mockMvc.perform(put("/api/clinical/patients/3333444455/cancel"))
                .andExpect(status().isConflict());

        BigDecimal receivables = billingService.getFinancialSummary().pendingReceivables();
        assertTrue(receivables.compareTo(new BigDecimal("150.00")) >= 0);
    }

    // ==================== ۳. تست‌های امور مالی، استرداد جزئی و کلید یکتا ====================

    @Test
    @DisplayName("۷. تسویه حساب با کلید یکتا (Idempotency) و جلوگیری از کسر دوباره بدهی")
    @WithMockUser(username = "cashier", authorities = {"ROLE_CASHIER", "CASHIER"})
    void settlementWithIdempotencyKeyShouldPreventDuplicateCharge() throws Exception {
        createTestPatient("4444555566", "سعید علوی", new BigDecimal("150.00"), PatientStatus.WAITING_FOR_PAYMENT);

        Map<String, Object> settleReq = Map.of(
                "patientNationalId", "4444555566",
                "amount", new BigDecimal("150.00"),
                "discountAmount", new BigDecimal("30.00"),
                "taxAmount", new BigDecimal("10.00"),
                "method", "DEBIT_CARD",
                "description", "تسویه تست یکتا",
                "idempotencyKey", "UNIQUE-KEY-4455"
        );

        String body = objectMapper.writeValueAsString(settleReq);

        String firstResp = mockMvc.perform(post("/api/billing/settle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netTotal").value(130.00))
                .andReturn().getResponse().getContentAsString();

        String firstReceiptId = objectMapper.readTree(firstResp).get("receiptId").asText();

        mockMvc.perform(post("/api/billing/settle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptId").value(firstReceiptId));

        Patient settledPatient = patientRepository.findByNationalId("4444555566").orElseThrow();
        assertEquals(PatientStatus.PAYMENT_COMPLETED, settledPatient.getCurrentStatus());
        assertEquals(0, settledPatient.getPendingBill().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("۸. تشخیص تعارض کلید یکتا (Idempotency Conflict) هنگام تغییر مبلغ یا بیمار")
    @WithMockUser(username = "cashier", authorities = {"ROLE_CASHIER", "CASHIER"})
    void reusingIdempotencyKeyWithDifferentPayloadShouldFail() throws Exception {
        createTestPatient("5555666677", "بیمار اول", new BigDecimal("200.00"), PatientStatus.WAITING_FOR_PAYMENT);

        Map<String, Object> firstReq = Map.of(
                "patientNationalId", "5555666677",
                "amount", new BigDecimal("100.00"),
                "discountAmount", BigDecimal.ZERO,
                "taxAmount", BigDecimal.ZERO,
                "method", "CASH",
                "idempotencyKey", "CONFLICT-KEY-99"
        );

        mockMvc.perform(post("/api/billing/settle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstReq)))
                .andExpect(status().isOk());

        Map<String, Object> conflictingReq = Map.of(
                "patientNationalId", "5555666677",
                "amount", new BigDecimal("50.00"),
                "discountAmount", BigDecimal.ZERO,
                "taxAmount", BigDecimal.ZERO,
                "method", "CASH",
                "idempotencyKey", "CONFLICT-KEY-99"
        );

        mockMvc.perform(post("/api/billing/settle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictingReq)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("۹. صحت محاسبات حسابداری در استرداد جزئی (Partial Refund Reconciliation)")
    @WithMockUser(username = "cashier", authorities = {"ROLE_CASHIER", "CASHIER"})
    void partialRefundShouldPreserveRemainingRevenueAndUpdateStatusToPartiallyRefunded() throws Exception {
        financialRepository.deleteAll();
        createTestPatient("6666777788", "مریم نوری", new BigDecimal("150.00"), PatientStatus.WAITING_FOR_PAYMENT);

        Map<String, Object> settleReq = Map.of(
                "patientNationalId", "6666777788",
                "amount", new BigDecimal("150.00"),
                "discountAmount", new BigDecimal("30.00"),
                "taxAmount", new BigDecimal("10.00"),
                "method", "CREDIT_CARD",
                "description", "تسویه قبل از استرداد جزئی"
        );

        String resp = mockMvc.perform(post("/api/billing/settle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settleReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String originalReceiptId = objectMapper.readTree(resp).get("receiptId").asText();

        Receipt originalBefore = financialRepository.findById(originalReceiptId).orElseThrow();
        assertEquals(Receipt.ReceiptStatus.PAID, originalBefore.getReceiptStatus());

        Admin financialAdmin = new Admin("1000000006", "مدیر مالی", 35, "09120000001",
                new BigDecimal("30000.00"), "6", "hash", Admin.AdminRole.FINANCIAL_ADMIN);

        billingService.issueSupervisedRefund(
                null, "6666777788", originalReceiptId, new BigDecimal("30.00"), "کسر خدمت لغوشده", financialAdmin
        );

        Receipt updatedOriginal = financialRepository.findById(originalReceiptId).orElseThrow();
        assertEquals(Receipt.ReceiptStatus.PARTIALLY_REFUNDED, updatedOriginal.getReceiptStatus());
        assertEquals(0, new BigDecimal("30.00").compareTo(updatedOriginal.getRefundedAmount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(updatedOriginal.getRemainingRefundableAmount()),
                "Refundable cash must not exceed net cash collected minus prior refunds");
        assertTrue(updatedOriginal.verifyIntegrity());

        FinancialSummaryResponse summary = billingService.getFinancialSummary();
        assertEquals(0, new BigDecimal("150.00").compareTo(summary.grossRevenue()));
        assertEquals(0, new BigDecimal("30.00").compareTo(summary.totalRefunded()));
        assertEquals(0, new BigDecimal("100.00").compareTo(summary.netCollectedRevenue()));
        assertTrue(summary.allReceiptsCryptographicallyIntact());
    }

    // ==================== ۴. تست‌های یکپارچگی رمزنگاری و زنجیره حسابرسی ====================

    @Test
    @DisplayName("۱۰. تشخیص دستکاری در مبالغ رسید توسط امضای HMAC-SHA256")
    void receiptCryptographicHashShouldDetectAnyTampering() throws Exception {
        Receipt receipt = new Receipt(
                "POS-TEST99",
                "1234567890",
                "0000000002",
                "cashier",
                new BigDecimal("150.00"),
                new BigDecimal("9.00"),
                new BigDecimal("15.00"),
                Receipt.PaymentMethod.CASH,
                Receipt.ReceiptStatus.PAID,
                "تست یکپارچگی رسید",
                SystemClock.now()
        );

        assertTrue(receipt.verifyIntegrity(), "رسید دستکاری‌نشده باید معتبر باشد");

        Field discountField = Receipt.class.getDeclaredField("discountAmount");
        discountField.setAccessible(true);
        discountField.set(receipt, new BigDecimal("100.00"));

        assertFalse(receipt.verifyIntegrity(), "تغییر تخفیف رسید باید باعث نامعتبر شدن امضای HMAC شود");
    }

    @Test
    @DisplayName("۱۱. تشخیص حذف کامل جدول لاگ‌های حسابرسی (Audit Chain Wipe Detection)")
    void emptyAuditTrailShouldBeDetectedAsCorrupted() {
        assertTrue(auditService.verifyChainIntegrity(), "زنجیره اولیه باید معتبر باشد");
        var preservedLogs = auditService.getAuditTrail();
        auditLogRepository.deleteAll();

        assertFalse(auditService.verifyChainIntegrity(), "خالی شدن جدول لاگ‌ها باید به عنوان خرابی زنجیره تشخیص داده شود");

        assertThrows(IllegalStateException.class, () -> auditService.logAction(
                "SYSTEM", "CORE", "GENESIS_BOOTSTRAP", "SUCCESS", "127.0.0.1", "blocked rebuild"));
        // Restore synthetic test records, not a new genesis that erases evidence of the wipe.
        auditLogRepository.saveAll(preservedLogs);
        assertTrue(auditService.verifyChainIntegrity());
    }
}