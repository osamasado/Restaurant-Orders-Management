package org.restaurantordersmanagement.backend.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.restaurantordersmanagement.backend.web.SpaPaths;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.staff.security.StaffSessionRecheckFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            StaffAccountRepository staffAccountRepository) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .securityContext(securityContext -> securityContext.securityContextRepository(securityContextRepository))
                // Right after the session's context is loaded: an account that was deleted, demoted or given a new PIN
                // must not keep working in a session opened before that.
                .addFilterAfter(
                        new StaffSessionRecheckFilter(staffAccountRepository, securityContextRepository),
                        SecurityContextHolderFilter.class)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .authorizeHttpRequests(authorize -> authorize
                        // sendError() (a 404 or 409 from a ResponseStatusException) forwards to /error as an
                        // ERROR dispatch. Left closed, an anonymous guest's 404 would turn into a 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // The complete public list, see Documentation/access-control.md. Adding a public
                        // endpoint means editing this list on purpose; everything else needs a signed-in
                        // account, and @PreAuthorize on the controller then narrows it to the right roles.
                        .requestMatchers(HttpMethod.POST, "/api/staff/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/guest/menu", "/api/guest/settings", "/api/hall/orders").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/guest/cart/quote", "/api/guest/device/claim", "/api/guest/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/guest/orders/*").permitAll()
                        // HEAD too: a proxy or a monitor checking that a picture is there asks with HEAD.
                        .requestMatchers(HttpMethod.GET, "/images/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/images/**").permitAll()
                        // The React app's own files and screens, when the backend serves them (SpaConfig).
                        // HEAD too: a monitor or a proxy checking that the page is there asks with HEAD.
                        .requestMatchers(HttpMethod.GET, SpaPaths.FILES).permitAll()
                        .requestMatchers(HttpMethod.GET, SpaPaths.SCREENS).permitAll()
                        .requestMatchers(HttpMethod.HEAD, SpaPaths.FILES).permitAll()
                        .requestMatchers(HttpMethod.HEAD, SpaPaths.SCREENS).permitAll()
                        .anyRequest().authenticated())
                .logout(logout -> logout
                        .logoutUrl("/api/staff/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)));
        return http.build();
    }

}
