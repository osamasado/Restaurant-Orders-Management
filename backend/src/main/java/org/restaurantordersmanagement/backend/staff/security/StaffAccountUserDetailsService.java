package org.restaurantordersmanagement.backend.staff.security;

import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class StaffAccountUserDetailsService implements UserDetailsService {

    private final StaffAccountRepository staffAccountRepository;

    public StaffAccountUserDetailsService(StaffAccountRepository staffAccountRepository) {
        this.staffAccountRepository = staffAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String name) throws UsernameNotFoundException {
        return staffAccountRepository.findByName(name)
                .map(StaffPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No staff account named " + name));
    }

}
