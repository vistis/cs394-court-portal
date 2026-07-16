package kh.edu.paragoniu.court_portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Matches the BCrypt hashes seeded in court-shared (strength 12).
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
        throws Exception {
        http
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers(
                        "/login",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/webjars/**",
                        "/favicon.ico"
                    )
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/cases/new")
                    .hasAuthority("CASE_CREATE")
                    .requestMatchers(HttpMethod.POST, "/cases")
                    .hasAuthority("CASE_CREATE")
                    .requestMatchers(HttpMethod.GET, "/cases/*/status")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.POST, "/cases/*/status")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.GET, "/cases/*/docket/new")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.POST, "/cases/*/docket")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.GET, "/cases/*/documents/new")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.POST, "/cases/*/documents")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.GET, "/cases/*/documents/*/motion-status")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.POST, "/cases/*/documents/*/motion-status")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.GET, "/cases/*/hearings/new")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.POST, "/cases/*/hearings")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.GET, "/cases/*/hearings/*/reschedule")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.POST, "/cases/*/hearings/*/reschedule")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.GET, "/cases/*/participants/add")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.POST, "/cases/*/participants")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.POST, "/cases/*/participants/*/remove")
                    .hasAuthority("CASE_UPDATE")
                    .requestMatchers(HttpMethod.GET, "/cases/*/documents/*/download")
                    .hasAuthority("CASE_VIEW")
                    .requestMatchers(HttpMethod.GET, "/cases/*/documents/*")
                    .hasAuthority("CASE_VIEW")
                    .requestMatchers(HttpMethod.GET, "/cases/*/documents")
                    .hasAuthority("CASE_VIEW")
                    .requestMatchers(HttpMethod.GET, "/cases/*/hearings")
                    .hasAuthority("CASE_VIEW")
                    .requestMatchers(HttpMethod.GET, "/cases/*/participants")
                    .hasAuthority("CASE_VIEW")
                    .requestMatchers(HttpMethod.GET, "/cases/*/docket")
                    .hasAuthority("CASE_VIEW")
                    .requestMatchers(HttpMethod.GET, "/cases")
                    .hasAuthority("CASE_VIEW")
                    .requestMatchers(HttpMethod.GET, "/cases/*")
                    .hasAuthority("CASE_VIEW")
                    .anyRequest()
                    .authenticated()
            )
            .formLogin(form ->
                form
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .usernameParameter("email")
                    .passwordParameter("password")
                    .defaultSuccessUrl("/cases", true)
                    .failureUrl("/login?error")
                    .permitAll()
            )
            .logout(logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout")
                    .permitAll()
            );
        return http.build();
    }
}
