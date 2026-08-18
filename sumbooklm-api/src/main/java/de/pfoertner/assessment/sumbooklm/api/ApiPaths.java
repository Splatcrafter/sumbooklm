package de.pfoertner.assessment.sumbooklm.api;

/**
 * Path constants shared by the transport layer and by the components that host it.
 *
 * <h2>Purpose</h2>
 * The prefix below which the application exposes its REST endpoints is referenced by more than one
 * module. Controllers use it to derive their request mappings, and the single page application host
 * uses it to distinguish API requests from navigation requests that have to be answered with the
 * application shell. Keeping the value in one place prevents the two sides from drifting apart.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ApiPaths {

    /**
     * Prefix below which all REST endpoints of the application are exposed, without a trailing slash.
     *
     * @since 0.1.0
     */
    public static final String BASE = "/api";

    private ApiPaths() {
        throw new AssertionError("ApiPaths is a constant holder and must not be instantiated");
    }
}
