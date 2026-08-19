package de.pfoertner.assessment.sumbooklm.api.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import de.pfoertner.assessment.sumbooklm.api.ApiPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses API requests that did not arrive over a secure connection.
 *
 * <h2>Refusing Rather Than Redirecting</h2>
 * The usual answer to a request that should have been encrypted is a redirect to the encrypted
 * address. That is the wrong answer here, because the requests this protects carry credentials: an
 * access token on every call and, on a question, the API key of the user. By the time a redirect is
 * written the secret has already crossed the network in the clear, and telling the client to send it
 * again changes nothing about the copy that was made. A refusal at least does not invite a second
 * transmission.
 *
 * <h2>Only Below the API Prefix</h2>
 * What is served outside the prefix is the application shell and its assets, which carry no
 * credentials. Refusing those as well would leave a visitor of a misconfigured deployment with a
 * blank page instead of an application that can tell them what is wrong.
 *
 * <h2>Whether a Request Was Secure</h2>
 * Behind a reverse proxy the connection to the application is plain, and what decides is the header
 * the proxy adds. That is honoured because the deployment sets a forwarded headers strategy, which is
 * also why this cannot be derived from the port a request arrived on.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class SecureTransportFilter extends OncePerRequestFilter {

    /**
     * Problem document returned for a refused request.
     */
    private static final String PROBLEM = """
            {"type":"about:blank","title":"Insecure transport","status":426,\
            "detail":"This API is only served over HTTPS"}""";

    /**
     * Prefix below which a request has to be secure.
     */
    private static final String PROTECTED_PREFIX = ApiPaths.BASE + "/";

    /**
     * Creates the filter. The instance holds no state.
     */
    public SecureTransportFilter() {
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        if (!request.isSecure() && request.getRequestURI().startsWith(PROTECTED_PREFIX)) {
            response.setStatus(HttpStatus.UPGRADE_REQUIRED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(PROBLEM);
            return;
        }
        chain.doFilter(request, response);
    }
}
