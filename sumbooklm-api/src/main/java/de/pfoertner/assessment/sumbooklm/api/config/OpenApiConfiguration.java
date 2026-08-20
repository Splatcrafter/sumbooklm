/*
 * Copyright (c) 2026 Erik Pförtner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.pfoertner.assessment.sumbooklm.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
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

    /**
     * Name the bearer authentication scheme is published under and referenced by in operations.
     */
    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    /**
     * Version string reported in the {@code info.version} field of the specification.
     */
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
     * Builds the descriptive part of the OpenAPI specification and its authentication scheme.
     *
     * @return an {@link OpenAPI} instance carrying title, version, description, license and the
     *         bearer scheme protected operations refer to
     */
    @Bean
    public OpenAPI sumbookLmOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SumbookLM API")
                        .version(this.applicationVersion)
                        .description("Endpoints for managing notebooks, ingesting sources and querying them with a language model.")
                        .license(new License().name("Proprietary")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token issued by the login or registration endpoint.")));
    }
}
