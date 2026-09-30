package service;

import exceptions.EntityNotFoundException;
import models.Staff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.StaffRepository;

import java.util.List;

@Service
public class StaffManagementService {

    private final StaffRepository staffRepository;

    public StaffManagementService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Transactional
    public Staff registerStaff(Staff staff) {
        if (staffRepository.existsByNationalId(staff.getNationalId())) {
            throw new IllegalArgumentException("Staff member with National ID " + staff.getNationalId() + " already registered.");
        }
        if (staffRepository.existsByUsername(staff.getUsername())) {
            throw new IllegalArgumentException("Username '" + staff.getUsername() + "' is already in use.");
        }
        return staffRepository.save(staff);
    }

    @Transactional(readOnly = true)
    public Staff getStaffByUsername(String username) {
        return staffRepository.findByUsername(username)
                .orElseThrow(() -> EntityNotFoundException.staffNotFound(username));
    }

    @Transactional(readOnly = true)
    public Staff getStaffByNationalId(String nationalId) {
        return staffRepository.findByNationalId(nationalId)
                .orElseThrow(() -> EntityNotFoundException.staffNotFound(nationalId));
    }

    @Transactional(readOnly = true)
    public List<Staff> getAllStaff() {
        return staffRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Staff> getStaffByRole(String role) {
        return staffRepository.findByRole(role);
    }

    @Transactional
    public void toggleStaffStatus(String username, boolean isActive) {
        Staff staff = getStaffByUsername(username);
        staff.setActive(isActive);
        staffRepository.save(staff);
    }
}