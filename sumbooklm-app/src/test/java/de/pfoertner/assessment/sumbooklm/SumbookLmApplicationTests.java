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

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the application context of the executable artifact starts up.
 *
 * <h2>Coverage</h2>
 * Starting the context exercises the auto configuration of every module that is assembled into the
 * artifact, which includes the JPA data source, the Aether Datafixers integration, the LangChain4j
 * starters and the OpenAPI endpoints.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@SpringBootTest
@ActiveProfiles("dev")
class SumbookLmApplicationTests {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    SumbookLmApplicationTests() {
    }

    /**
     * Starts the application context and fails when any bean cannot be created.
     */
    @Test
    void contextLoads() {
    }
}
