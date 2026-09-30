package repository;

import enums.PatientStatus;
import models.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PatientRepository extends JpaRepository<Patient, String>, JpaSpecificationExecutor<Patient> {
    Optional<Patient> findByNationalId(String nationalId);
    boolean existsByNationalId(String nationalId);
    List<Patient> findByCurrentStatus(PatientStatus currentStatus);
    List<Patient> findByCurrentStatusIn(Set<PatientStatus> statuses);
    void deleteByNationalId(String nationalId);
}