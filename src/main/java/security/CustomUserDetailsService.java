package security;

import models.Admin;
import models.Staff;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import repository.StaffRepository;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final StaffRepository staffRepository;

    public CustomUserDetailsService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Staff staff = staffRepository.findByUsername(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("کاربر یافت نشد: " + username));

        if (!staff.isActive()) {
            throw new UsernameNotFoundException("حساب کاربری مسدود است.");
        }

        String role = staff.getRole();
        if (staff instanceof Admin admin) {
            role = admin.getAdminRole().name();
        }

        String authority = role.startsWith("ROLE_") ? role.toUpperCase() : "ROLE_" + role.toUpperCase();

        String password = staff.getEncodedPassword();
        if ((password == null || password.isBlank()) && staff instanceof Admin admin) {
            password = admin.getPasswordHash();
        }
        if (password == null) {
            password = "";
        }

        return new User(
                staff.getUsername(),
                password,
                Collections.singleton(new SimpleGrantedAuthority(authority))
        );
    }
}