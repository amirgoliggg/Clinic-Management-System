package com.clinic.repository;

import com.clinic.models.Receipt;
import com.clinic.models.Receipt.PaymentMethod;
import com.clinic.models.Receipt.ReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialRepository extends JpaRepository<Receipt, String>, JpaSpecificationExecutor<Receipt> {

    List<Receipt> findByPatientNationalIdOrderByTimestampDesc(String patientNationalId);

    List<Receipt> findByPatientNationalId(String patientNationalId);

    List<Receipt> findByDoctorNationalId(String doctorNationalId);

    List<Receipt> findByCashierUsername(String cashierUsername);

    List<Receipt> findByPaymentMethod(PaymentMethod paymentMethod);

    List<Receipt> findByReceiptStatus(ReceiptStatus receiptStatus);

    Optional<Receipt> findByDescriptionContaining(String idempotencyToken);

    Optional<Receipt> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Receipt r " +
            "WHERE r.receiptId NOT LIKE 'REF-%' AND r.receiptStatus NOT IN ('VOIDED', 'PENDING')")
    BigDecimal calculateTotalGrossRevenue();

    @Query("SELECT COALESCE(SUM(r.taxAmount), 0) FROM Receipt r " +
            "WHERE r.receiptId NOT LIKE 'REF-%' AND r.receiptStatus NOT IN ('VOIDED', 'PENDING')")
    BigDecimal calculateTotalTaxCollected();

    @Query("SELECT COALESCE(SUM(r.discountAmount), 0) FROM Receipt r " +
            "WHERE r.receiptId NOT LIKE 'REF-%' AND r.receiptStatus NOT IN ('VOIDED', 'PENDING')")
    BigDecimal calculateTotalDiscountsGiven();

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Receipt r " +
            "WHERE r.receiptId LIKE 'REF-%' AND r.receiptStatus = 'REFUNDED'")
    BigDecimal calculateTotalRefundedAmount();

    default BigDecimal calculateTotalRevenue() {
        BigDecimal gross = calculateTotalGrossRevenue();
        BigDecimal tax = calculateTotalTaxCollected();
        BigDecimal disc = calculateTotalDiscountsGiven();
        BigDecimal refunded = calculateTotalRefundedAmount();
        return gross.add(tax).subtract(disc).subtract(refunded);
    }
}