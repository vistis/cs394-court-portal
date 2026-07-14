package kh.edu.paragoniu.court_portal.security;

import java.util.LinkedHashSet;
import java.util.Set;
import kh.edu.paragoniu.court_shared.entity.SystemRole;
import kh.edu.paragoniu.court_shared.entity.User;
import kh.edu.paragoniu.court_shared.entity.UserRole;
import kh.edu.paragoniu.court_shared.repository.RolePermissionRepository;
import kh.edu.paragoniu.court_shared.repository.UserRepository;
import kh.edu.paragoniu.court_shared.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a court user by email (the login identifier for this panel) and
 * resolves their granted authorities. Roles become {@code ROLE_<name>}
 * authorities (e.g. {@code ROLE_GREFFIER}); each role's permissions are added
 * as plain-code authorities (e.g. {@code CASE_VIEW}). Inactive users are
 * treated as not found, so they cannot authenticate.
 */
@Service
@RequiredArgsConstructor
public class GreffierUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        // Spring's UserDetailsService contract calls the login identifier a
        // "username"; for this panel that identifier is the user's email.
        User user = userRepository
            .findActiveByEmail(email)
            .orElseThrow(() ->
                new UsernameNotFoundException(
                    "No active user found for email: " + email
                )
            );

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (UserRole userRole : userRoleRepository.findByIdUserId(
            user.getUserId()
        )) {
            SystemRole role = userRole.getSystemRole();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            rolePermissionRepository
                .findByIdSystemRoleId(role.getSystemRoleId())
                .forEach(rp ->
                    authorities.add(
                        new SimpleGrantedAuthority(
                            rp.getSystemPermission().getCode()
                        )
                    )
                );
        }

        return new GreffierUserDetails(
            user.getUserId(),
            user.getUsername(),
            user.getPassword(),
            user.getFirstName() + " " + user.getLastName(),
            user.isActive(),
            authorities
        );
    }
}
