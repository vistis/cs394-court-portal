package kh.edu.paragoniu.court_portal.security;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GreffierUserDetailsService implements UserDetailsService {

    private final EntityManager entityManager;

    public GreffierUserDetailsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        AuthUser authUser = entityManager
            .createQuery(
                """
                SELECT new kh.edu.paragoniu.court_portal.security.AuthUser(
                    u.userId,
                    u.username,
                    u.password,
                    CONCAT(u.firstName, ' ', u.lastName),
                    u.isActive
                )
                FROM User u
                WHERE LOWER(u.email) = LOWER(:email)
                AND u.isActive = true
                """,
                AuthUser.class
            )
            .setParameter("email", email)
            .getResultStream()
            .findFirst()
            .orElseThrow(() ->
                new UsernameNotFoundException(
                    "No active user found for email: " + email
                )
            );

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (RoleGrant role : findRoles(authUser.userId())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
            findPermissions(role.systemRoleId())
                .forEach(permission ->
                    authorities.add(new SimpleGrantedAuthority(permission))
                );
        }

        return new GreffierUserDetails(
            authUser.userId(),
            authUser.username(),
            authUser.password(),
            authUser.displayName(),
            authUser.active(),
            authorities
        );
    }

    private List<RoleGrant> findRoles(UUID userId) {
        return entityManager
            .createQuery(
                """
                SELECT new kh.edu.paragoniu.court_portal.security.RoleGrant(
                    sr.systemRoleId,
                    sr.name
                )
                FROM UserRole ur
                JOIN ur.systemRole sr
                WHERE ur.id.userId = :userId
                ORDER BY sr.name
                """,
                RoleGrant.class
            )
            .setParameter("userId", userId)
            .getResultList();
    }

    private List<String> findPermissions(Integer systemRoleId) {
        return entityManager
            .createQuery(
                """
                SELECT sp.code
                FROM RolePermission rp
                JOIN rp.systemPermission sp
                WHERE rp.id.systemRoleId = :systemRoleId
                ORDER BY sp.code
                """,
                String.class
            )
            .setParameter("systemRoleId", systemRoleId)
            .getResultList();
    }
}
