package de.pfoertner.assessment.sumbooklm.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the source of the current time the application records timestamps from.
 *
 * <h2>Why It Lives Here</h2>
 * More than one module records timestamps, and all of them have to agree on what now is. The clock
 * is therefore supplied by the composition root instead of by whichever module happened to need it
 * first, which also keeps a test free to replace it in one place.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    /**
     * Creates the configuration. The instance is created by the container and holds no state.
     */
    public TimeConfiguration() {
    }

    /**
     * Provides the source of the current time.
     *
     * @return a clock in UTC, so that recorded timestamps do not depend on the zone of the host
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
