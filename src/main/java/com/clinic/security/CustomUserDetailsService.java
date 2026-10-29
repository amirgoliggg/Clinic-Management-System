package com.clinic.security;

import com.clinic.models.*;
import com.clinic.repository.StaffRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final StaffRepository staffRepository;

    public CustomUserDetailsService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("نام کاربری الزامی است.");
        }

        Staff staff = staffRepository.findByUsername(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("کاربر یافت نشد: " + username));

        if (!staff.isActive()) {
            throw new UsernameNotFoundException("حساب کاربری غیرفعال است.");
        }

        if (staff instanceof Admin admin && admin.isAccountLocked()) {
            throw new UsernameNotFoundException("حساب کاربری مدیریتی قفل شده است.");
        }

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        if (staff instanceof Admin admin && admin.getAdminRole() != null) {
            String adminRole = admin.getAdminRole().name().toUpperCase();
            authorities.add(new SimpleGrantedAuthority(adminRole));
            authorities.add(new SimpleGrantedAuthority("ROLE_" + adminRole));
        } else {
            String role = (staff.getRole() != null) ? staff.getRole().toUpperCase().replace("ROLE_", "") : "STAFF";
            authorities.add(new SimpleGrantedAuthority(role));
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

            // اعطای سطح دسترسی پایه بر اساس نوع موجودیت برای نقش‌های سفارشی
            if (staff instanceof Doctor) {
                authorities.add(new SimpleGrantedAuthority("DOCTOR"));
                authorities.add(new SimpleGrantedAuthority("ROLE_DOCTOR"));
            } else if (staff instanceof Nurse) {
                authorities.add(new SimpleGrantedAuthority("NURSE"));
                authorities.add(new SimpleGrantedAuthority("ROLE_NURSE"));
            } else if (staff instanceof Cashier) {
                authorities.add(new SimpleGrantedAuthority("CASHIER"));
                authorities.add(new SimpleGrantedAuthority("ROLE_CASHIER"));
            } else if (staff instanceof Receptionist) {
                authorities.add(new SimpleGrantedAuthority("RECEPTIONIST"));
                authorities.add(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"));
            }
        }

        String password = staff.getEncodedPassword();
        if (password == null) {
            password = "";
        }

        return new User(
                staff.getUsername(),
                password,
                staff.isActive(),
                true,
                true,
                !(staff instanceof Admin a && a.isAccountLocked()),
                authorities
        );
    }
}