package kh.edu.paragoniu.court_portal.legal;

import java.io.Serializable;

/**
 * View-model for the Judge Profile page. Judges have no firm in the fixed
 * schema, so the information card is First/Last name + Bar Number only.
 * {@code profileImageUrl} is null unless a real servable URL is stored (else the
 * view shows the initials avatar). Serializable so it can be cached in Redis.
 */
public record JudgeProfile(
    String judgeId,
    String name,
    String initials,
    String firstName,
    String lastName,
    String barNumber,
    String profileImageUrl
) implements Serializable {}
