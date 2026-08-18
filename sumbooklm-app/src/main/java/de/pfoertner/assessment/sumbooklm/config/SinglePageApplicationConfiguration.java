package de.pfoertner.assessment.sumbooklm.config;

import java.io.IOException;

import de.pfoertner.assessment.sumbooklm.api.ApiPaths;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Hosts the compiled single page application and answers unknown paths with its entry document.
 *
 * <h2>Why This Exists</h2>
 * The frontend uses client side routing. A deep link such as {@code /notebooks/42} is a valid
 * application route but not a file on the classpath, and the default static resource handling would
 * answer it with 404 before the browser ever loads the router. This configuration therefore serves
 * {@code index.html} for every path that neither resolves to a packaged asset nor belongs to the
 * REST API, which lets the router take over once the document is loaded.
 *
 * <h2>Resolution Order</h2>
 * <ul>
 *   <li>Requests below {@link ApiPaths#BASE} are never rewritten, so an unmatched API path keeps
 *       returning 404 instead of an HTML document.</li>
 *   <li>Paths that resolve to a packaged asset are served as that asset.</li>
 *   <li>Every remaining path is served the application shell.</li>
 * </ul>
 *
 * <h2>Registration</h2>
 * The handler is registered for the {@code /**} pattern. Spring Boot registers the same pattern for
 * its default static resource handling, and the registration contributed here replaces it because
 * application configurers are applied after the auto configuration. More specific patterns such as
 * the ones contributed by the OpenAPI user interface remain unaffected.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Configuration
public class SinglePageApplicationConfiguration implements WebMvcConfigurer {

    /**
     * Classpath location the frontend build copies its compiled assets into.
     */
    private static final String STATIC_LOCATION = "classpath:/static/";

    /**
     * Classpath relative location of the application shell served for client side routes.
     */
    private static final String ENTRY_DOCUMENT = "static/index.html";

    /**
     * API prefix in the form resource paths are resolved with, without a leading slash and with
     * a trailing slash, so that it can be matched against the path passed to the resolver.
     */
    private static final String API_RESOURCE_PREFIX = ApiPaths.BASE.substring(1) + "/";

    /**
     * Creates the configuration. The instance is created by the container and holds no state.
     */
    public SinglePageApplicationConfiguration() {
    }

    /**
     * Registers the resource handler that serves the packaged frontend and its entry document.
     *
     * <p>The handler is bound to the {@code /**} pattern and resolves against
     * {@value #STATIC_LOCATION}. Resolution runs through a resource chain whose last resolver
     * falls back to the application shell, which is what makes client side routes reachable by
     * direct request.
     *
     * @param registry registry the resource handler is contributed to
     */
    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_LOCATION)
                .resourceChain(true)
                .addResolver(new SinglePageApplicationResourceResolver());
    }

    /**
     * Resolves packaged assets and falls back to the application shell for client side routes.
     */
    private static final class SinglePageApplicationResourceResolver extends PathResourceResolver {

        /**
         * Application shell returned for every path that is neither a packaged asset nor an API
         * path. The resource is resolved once and re-read on each request by the servlet layer.
         */
        private final Resource entryDocument = new ClassPathResource(ENTRY_DOCUMENT);

        /**
         * Creates the resolver and looks up the application shell on the classpath.
         */
        private SinglePageApplicationResourceResolver() {
        }

        /**
         * Resolves a request path against the packaged assets of the frontend.
         *
         * @param resourcePath requested path relative to the resource location, without a leading slash
         * @param location     classpath location the frontend assets were packaged into
         * @return the requested asset, the application shell when the path is a client side route,
         *         or {@code null} when the path belongs to the REST API or the shell is not packaged
         * @throws IOException if the underlying resource cannot be inspected
         */
        @Override
        protected @Nullable Resource getResource(final String resourcePath, final Resource location) throws IOException {
            final Resource requested = super.getResource(resourcePath, location);
            if (requested != null) {
                return requested;
            }
            if (resourcePath.startsWith(API_RESOURCE_PREFIX)) {
                return null;
            }
            return this.entryDocument.exists() ? this.entryDocument : null;
        }
    }
}
