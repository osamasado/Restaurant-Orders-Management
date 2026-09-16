package org.restaurantordersmanagement.backend.staff.security;

import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class StaffPrincipal implements UserDetails {

    private final StaffAccount staffAccount;

    public StaffPrincipal(StaffAccount staffAccount) {
        this.staffAccount = staffAccount;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + staffAccount.getRole().name()));
    }

    @Override
    public String getPassword() {
        return staffAccount.getPinHash();
    }

    @Override
    public String getUsername() {
        return staffAccount.getName();
    }

}
