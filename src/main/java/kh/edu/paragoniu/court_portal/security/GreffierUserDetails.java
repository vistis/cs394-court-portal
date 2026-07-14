package kh.edu.paragoniu.court_portal.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated principal for the Greffier panel. Kept lightweight and
 * {@link Serializable} so it can live in the Redis-backed HTTP session.
 */
public class GreffierUserDetails implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String username;
    private final String password;
    private final String displayName;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public GreffierUserDetails(
        UUID userId,
        String username,
        String password,
        String displayName,
        boolean active,
        Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.active = active;
        this.authorities = authorities;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
