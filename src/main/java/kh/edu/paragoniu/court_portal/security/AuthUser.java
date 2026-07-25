package kh.edu.paragoniu.court_portal.security;

import java.util.UUID;

public record AuthUser(
    UUID userId,
    String username,
    String password,
    String displayName,
    boolean active
) implements java.io.Serializable {}
