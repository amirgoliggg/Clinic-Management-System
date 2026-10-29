package com.clinic.repository;

import com.clinic.models.MedicalRecord;
import com.clinic.models.MedicalRecord.RecordStatus;
import com.clinic.models.MedicalRecord.RecordType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, String>, JpaSpecificationExecutor<MedicalRecord> {
    List<MedicalRecord> findByPatientNationalId(String patientNationalId);

    List<MedicalRecord> findByDoctorNationalId(String doctorNationalId);

    List<MedicalRecord> findByRecordType(RecordType recordType);

    List<MedicalRecord> findByRecordStatus(RecordStatus recordStatus);
}