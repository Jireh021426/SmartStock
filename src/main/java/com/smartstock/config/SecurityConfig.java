
package com.smartstock.config;

import org.springframework.security.web.access.intercept.AuthorizationFilter;

import com.smartstock.repository.EmployeeRepository;

import com.smartstock.service.EmployeeUserDetailsService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService temporaryAdminService(
            PasswordEncoder encoder) {

        String password =
                System.getenv("SMARTSTOCK_ADMIN_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "SMARTSTOCK_ADMIN_PASSWORD is required.");
        }

        UserDetails admin = User.builder()
                .username("admin")
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            EmployeeUserDetailsService employeeService,
            @Qualifier("temporaryAdminService")
            UserDetailsService temporaryAdminService,
            PasswordEncoder encoder) {

        DaoAuthenticationProvider employeeProvider =
                new DaoAuthenticationProvider();

        employeeProvider.setUserDetailsService(employeeService);
        employeeProvider.setPasswordEncoder(encoder);

        DaoAuthenticationProvider adminProvider =
                new DaoAuthenticationProvider();

        adminProvider.setUserDetailsService(
                temporaryAdminService);
        adminProvider.setPasswordEncoder(encoder);

        return new ProviderManager(
                List.of(employeeProvider, adminProvider));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            EmployeeRepository employeeRepository)
            throws Exception {

        http.authenticationManager(authenticationManager)

            .addFilterBefore(
                    new EmployeeSessionValidationFilter(employeeRepository),
                    AuthorizationFilter.class
            )

            .authorizeHttpRequests(auth -> auth

                // Public static resources
                .requestMatchers(
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico"
                ).permitAll()

                // Financial dashboard
                .requestMatchers(
                        HttpMethod.GET,
                        "/",
                        "/dashboard"
                ).hasAnyRole("ADMIN", "MANAGER")

                // General home and product catalog
                .requestMatchers(
                        HttpMethod.GET,
                        "/home",
                        "/products"
                ).authenticated()

                // Employee administration
                .requestMatchers(
                        "/employees",
                        "/employees/**"
                ).hasRole("ADMIN")

                // Product administration
                .requestMatchers(
                        HttpMethod.GET,
                        "/products/new",
                        "/products/*/edit"
                ).hasRole("ADMIN")

                .requestMatchers(
                        HttpMethod.POST,
                        "/products/save",
                        "/products/*/save",
                        "/products/*/delete"
                ).hasRole("ADMIN")

                .requestMatchers("/products/**").denyAll()

                // Location visibility
                .requestMatchers(
                        HttpMethod.GET,
                        "/locations",
                        "/locations/"
                ).hasAnyRole(
                        "ADMIN", "MANAGER", "AUDITOR"
                )

                // Location management
                .requestMatchers("/locations/**")
                .hasRole("ADMIN")

                // Inventory history
                .requestMatchers(
                        HttpMethod.GET,
                        "/inventory",
                        "/inventory/"
                ).hasAnyRole(
                        "ADMIN",
                        "MANAGER",
                        "AUDITOR",
                        "SENDER",
                        "RECEIVER"
                )

                // Manual inventory adjustments
                .requestMatchers(
                        HttpMethod.GET,
                        "/inventory/new"
                ).hasRole("ADMIN")

                .requestMatchers(
                        HttpMethod.POST,
                        "/inventory/save"
                ).hasRole("ADMIN")

                .requestMatchers("/inventory/**").denyAll()

                // Stock transfers
                .requestMatchers(
                        HttpMethod.POST,
                        "/transfers/dispatch"
                ).hasRole("SENDER")

                .requestMatchers(
                        HttpMethod.POST,
                        "/transfers/receive"
                ).hasRole("RECEIVER")

                .requestMatchers(
                        HttpMethod.POST,
                        "/transfers/resolve"
                ).hasAnyRole("ADMIN", "MANAGER")

                .requestMatchers(
                        HttpMethod.GET,
                        "/transfers",
                        "/transfers/"
                ).hasAnyRole(
                        "ADMIN",
                        "MANAGER",
                        "SENDER",
                        "RECEIVER",
                        "AUDITOR"
                )

                .requestMatchers("/transfers/**").denyAll()

                // Physical stock audits
                .requestMatchers(
                        HttpMethod.GET,
                        "/audits",
                        "/audits/"
                ).hasAnyRole(
                        "ADMIN",
                        "MANAGER",
                        "AUDITOR"
                )

                .requestMatchers(
                        HttpMethod.POST,
                        "/audits/count"
                ).hasRole("AUDITOR")

                .requestMatchers(
                        HttpMethod.POST,
                        "/audits/review"
                ).hasAnyRole("ADMIN", "MANAGER")

                .requestMatchers("/audits/**").denyAll()

                // Sales: read-only access for auditors
                .requestMatchers(
                        HttpMethod.GET,
                        "/sales"
                ).hasAnyRole(
                        "ADMIN",
                        "MANAGER",
                        "CASHIER",
                        "AUDITOR"
                )

                // Sale creation is cashier-only
                .requestMatchers(
                        HttpMethod.GET,
                        "/sales/new"
                ).hasRole("CASHIER")

                .requestMatchers(
                        HttpMethod.POST,
                        "/sales/save"
                ).hasRole("CASHIER")

                // Individual receipts
                .requestMatchers(
                        HttpMethod.GET,
                        "/sales/*"
                ).hasAnyRole(
                        "ADMIN",
                        "MANAGER",
                        "CASHIER",
                        "AUDITOR"
                )

                .requestMatchers("/sales/**").denyAll()

                // Customer and credit information
                .requestMatchers(
                        HttpMethod.GET,
                        "/customers"
                ).hasAnyRole(
                        "ADMIN",
                        "MANAGER",
                        "CASHIER",
                        "AUDITOR"
                )

                // Customer creation
                .requestMatchers(
                        HttpMethod.GET,
                        "/customers/new"
                ).hasRole("CASHIER")

                .requestMatchers(
                        HttpMethod.POST,
                        "/customers/save"
                ).hasRole("CASHIER")

                .requestMatchers("/customers/**").denyAll()

                // Customer credit repayments
                .requestMatchers(
                        HttpMethod.GET,
                        "/payments/new"
                ).hasRole("CASHIER")

                .requestMatchers(
                        HttpMethod.POST,
                        "/payments/save"
                ).hasRole("CASHIER")

                .requestMatchers("/payments/**").denyAll()

                // Remittance viewing
                .requestMatchers(
                        HttpMethod.GET,
                        "/remittances"
                ).hasAnyRole(
                        "ADMIN",
                        "MANAGER",
                        "CASHIER"
                )

                // Cashier submits own remittance
                .requestMatchers(
                        HttpMethod.POST,
                        "/remittances/submit"
                ).hasRole("CASHIER")

                // Management verifies remittances
                .requestMatchers(
                        HttpMethod.POST,
                        "/remittances/verify"
                ).hasAnyRole("ADMIN", "MANAGER")

                .requestMatchers("/remittances/**").denyAll()

                // Expenses
                .requestMatchers(
                        HttpMethod.POST,
                        "/expenses"
                ).hasAnyRole("ADMIN", "MANAGER")

                // Explicit default-deny policy
                .anyRequest().denyAll()
            )

            .formLogin(form -> form
                    .defaultSuccessUrl("/products", false)
                    .permitAll()
            )

            .logout(logout -> logout
                    .logoutSuccessUrl("/login?logout")
                    .permitAll()
            );

        return http.build();
    }
}
