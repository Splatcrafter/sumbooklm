package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the settings of the extraction package available as a bean.
 *
 * <h2>Why It Declares Nothing Else</h2>
 * The extractors and the resolver are components the container finds on its own. What it does not
 * find on its own is the settings record, because binding it has to be asked for, and a module that
 * asks for its own settings keeps the composition root free of knowledge about what this module
 * happens to be configurable by.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebSourceProperties.class)
public class ExtractionConfiguration {

    /**
     * Creates the configuration. The instance is created by the container and holds no state.
     */
    public ExtractionConfiguration() {
    }
}
