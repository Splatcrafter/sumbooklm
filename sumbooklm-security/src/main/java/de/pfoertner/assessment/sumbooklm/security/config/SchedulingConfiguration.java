package de.pfoertner.assessment.sumbooklm.security.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the scheduler the refresh token cleanup runs on.
 *
 * <h2>Placement</h2>
 * Scheduling is enabled here rather than in the application module, because the security module is
 * what contributes the only scheduled job. Assembling the application therefore cannot forget to
 * switch it on.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfiguration {

    /**
     * Creates the configuration. The instance is created by the container and holds no state.
     */
    public SchedulingConfiguration() {
    }
}
