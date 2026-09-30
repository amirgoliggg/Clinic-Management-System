package repository;

import models.Receipt;
import models.Receipt.PaymentMethod;
import models.Receipt.ReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface FinancialRepository extends JpaRepository<Receipt, String>, JpaSpecificationExecutor<Receipt> {
    List<Receipt> findByPatientNationalId(String patientNationalId);
    List<Receipt> findByDoctorNationalId(String doctorNationalId);
    List<Receipt> findByCashierUsername(String cashierUsername);
    List<Receipt> findByPaymentMethod(PaymentMethod paymentMethod);
    List<Receipt> findByReceiptStatus(ReceiptStatus receiptStatus);

    @Query("SELECT SUM(r.amount) FROM Receipt r WHERE r.receiptStatus = 'PAID'")
    BigDecimal calculateTotalRevenue();
}