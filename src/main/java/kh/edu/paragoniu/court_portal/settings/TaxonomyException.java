package kh.edu.paragoniu.court_portal.settings;

/** Thrown when a taxonomy add/delete is rejected (validation, duplicate, in use). */
public class TaxonomyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TaxonomyException(String message) {
        super(message);
    }
}
