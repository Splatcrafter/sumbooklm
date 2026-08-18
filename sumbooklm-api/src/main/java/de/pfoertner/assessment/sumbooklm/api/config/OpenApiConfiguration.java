package de.pfoertner.assessment.sumbooklm.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the descriptive metadata of the OpenAPI specification published by the application.
 *
 * <h2>Effect</h2>
 * springdoc discovers the {@link OpenAPI} bean contributed by this configuration and merges it with
 * the operations it derives from the controllers of the transport layer. The resulting document is
 * served as JSON and rendered by the bundled Swagger UI.
 *
 * <h2>Consumers</h2>
 * The frontend build reads the generated document and emits TypeScript definitions from it, which
 * makes this bean part of the contract between backend and frontend rather than presentation only.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Configuration
public class OpenApiConfiguration {

    private final String applicationVersion;

    /**
     * Creates the configuration.
     *
     * @param applicationVersion version reported in the specification, resolved from the
     *                           {@code spring.application.version} property and defaulting to
     *                           {@code 0.1.0} when the property is absent
     */
    public OpenApiConfiguration(@Value("${spring.application.version:0.1.0}") final String applicationVersion) {
        this.applicationVersion = applicationVersion;
    }

    /**
     * Builds the descriptive part of the OpenAPI specification.
     *
     * @return an {@link OpenAPI} instance carrying title, version, description and license of the API
     */
    @Bean
    public OpenAPI sumbookLmOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SumbookLM API")
                .version(this.applicationVersion)
                .description("Endpoints for managing notebooks, ingesting sources and querying them with a language model.")
                .license(new License().name("Proprietary")));
    }
}
