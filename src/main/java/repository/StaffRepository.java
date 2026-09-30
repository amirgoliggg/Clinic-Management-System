package repository;

import models.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, String>, JpaSpecificationExecutor<Staff> {
    Optional<Staff> findByUsername(String username);
    Optional<Staff> findByNationalId(String nationalId);
    boolean existsByUsername(String username);
    boolean existsByNationalId(String nationalId);
    List<Staff> findByRole(String role);
    void deleteByUsername(String username);
    void deleteByNationalId(String nationalId);
}