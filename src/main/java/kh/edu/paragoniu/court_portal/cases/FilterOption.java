package kh.edu.paragoniu.court_portal.cases;

import java.io.Serializable;

/** Serializable so cached (Redis) reference-data lists survive round-trips. */
public record FilterOption(Integer id, String name) implements Serializable {}
