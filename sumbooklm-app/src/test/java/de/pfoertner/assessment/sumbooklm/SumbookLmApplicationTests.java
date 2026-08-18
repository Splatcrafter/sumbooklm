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
     * Starts the application context and fails when any bean cannot be created.
     *
     * @since 0.1.0
     */
    @Test
    void contextLoads() {
    }
}
