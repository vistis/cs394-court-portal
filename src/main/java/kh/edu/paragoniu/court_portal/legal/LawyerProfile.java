package kh.edu.paragoniu.court_portal.legal;

import java.io.Serializable;

/**
 * View-model for the Lawyer Profile page. {@code profileImageUrl} is null unless
 * a real servable URL is stored (otherwise the view shows the initials avatar).
 * {@code firmOffice} is "—" when the lawyer has no firm. Serializable so it can
 * be cached in Redis.
 */
public record LawyerProfile(
    String lawyerId,
    String name,
    String initials,
    String firstName,
    String lastName,
    String barNumber,
    String firmOffice,
    String profileImageUrl
) implements Serializable {}
