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

package de.pfoertner.assessment.sumbooklm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the executable application artifact.
 *
 * <h2>Composition</h2>
 * The class is located in the root package of the application, so component scanning covers the
 * domain, persistence, ingestion, AI and API modules without further configuration. The produced
 * artifact bundles the REST API and the compiled single page application in one executable JAR.
 *
 * <h2>Profiles</h2>
 * <ul>
 *   <li>{@code dev} activates an in memory H2 database and is the default when no profile is set.</li>
 *   <li>{@code prod} expects an external PostgreSQL instance.</li>
 * </ul>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@SpringBootApplication
public class SumbookLmApplication {

    /**
     * Creates the configuration class that carries the application entry point. The instance is
     * created by the container during component scanning and holds no state.
     */
    public SumbookLmApplication() {
    }

    /**
     * Starts the Spring application context and the embedded web server.
     *
     * @param args command line arguments forwarded to Spring Boot, never {@code null}
     */
    static void main(final String[] args) {
        SpringApplication.run(SumbookLmApplication.class, args);
    }
}
