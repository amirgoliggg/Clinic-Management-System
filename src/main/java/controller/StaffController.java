package controller;

import models.Staff;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import service.StaffManagementService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/staff")
public class StaffController {

    private final StaffManagementService staffManagementService;

    public StaffController(StaffManagementService staffManagementService) {
        this.staffManagementService = staffManagementService;
    }

    @PostMapping("/register")
    public ResponseEntity<Staff> registerStaff(@RequestBody Staff staff) {
        return ResponseEntity.ok(staffManagementService.registerStaff(staff));
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<Staff> getStaffByUsername(@PathVariable String username) {
        return ResponseEntity.ok(staffManagementService.getStaffByUsername(username));
    }

    @GetMapping("/nid/{nationalId}")
    public ResponseEntity<Staff> getStaffByNationalId(@PathVariable String nationalId) {
        return ResponseEntity.ok(staffManagementService.getStaffByNationalId(nationalId));
    }

    @GetMapping
    public ResponseEntity<List<Staff>> getAllStaff() {
        return ResponseEntity.ok(staffManagementService.getAllStaff());
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<Staff>> getStaffByRole(@PathVariable String role) {
        return ResponseEntity.ok(staffManagementService.getStaffByRole(role));
    }

    @PutMapping("/{username}/status")
    public ResponseEntity<Void> toggleStaffStatus(@PathVariable String username, @RequestParam boolean isActive) {
        staffManagementService.toggleStaffStatus(username, isActive);
        return ResponseEntity.ok().build();
    }
}