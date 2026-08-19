package de.pfoertner.assessment.sumbooklm.api.v1.chat;

/**
 * Names of the headers a caller presents their own model access in.
 *
 * <h2>Why Headers</h2>
 * The values describe how the request is to be carried out rather than what is being asked, and one
 * of them is a credential. Putting a key into the body would place it in a request that is otherwise
 * a question, and into every log line that records a body.
 *
 * <h2>Not Stored</h2>
 * None of the values is persisted. They are read for one request, turned into a client, and forgotten
 * with the response.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ByokHeaders {

    /**
     * Name of the service the model is requested from.
     */
    public static final String PROVIDER = "X-AI-Provider";

    /**
     * Key the service is addressed with, absent for a service that needs none.
     */
    public static final String API_KEY = "X-AI-Api-Key";

    /**
     * Name the service knows the model under.
     */
    public static final String MODEL = "X-AI-Model";

    /**
     * Address the service is reached at, absent for the default address of the service.
     */
    public static final String BASE_URL = "X-AI-Base-Url";

    /**
     * Prevents instantiation of this constant holder.
     */
    private ByokHeaders() {
        throw new AssertionError("ByokHeaders is a constant holder and must not be instantiated");
    }
}
