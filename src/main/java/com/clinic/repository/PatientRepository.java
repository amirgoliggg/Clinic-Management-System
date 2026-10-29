package com.clinic.repository;

import com.clinic.enums.PatientStatus;
import com.clinic.models.Patient;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PatientRepository extends JpaRepository<Patient, String>, JpaSpecificationExecutor<Patient> {

    Optional<Patient> findByNationalId(String nationalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Patient p WHERE p.nationalId = :nationalId")
    Optional<Patient> findByNationalIdForUpdate(@Param("nationalId") String nationalId);

    boolean existsByNationalId(String nationalId);

    List<Patient> findByCurrentStatus(PatientStatus currentStatus);

    List<Patient> findByCurrentStatusIn(Set<PatientStatus> statuses);

    void deleteByNationalId(String nationalId);

    @Query("SELECT COALESCE(SUM(p.pendingBill), 0) FROM Patient p WHERE p.pendingBill > 0 AND p.currentStatus <> 'CANCELLED'")
    BigDecimal calculateTotalPendingReceivables();
}