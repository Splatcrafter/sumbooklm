package de.pfoertner.assessment.sumbooklm.workspace.chat;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the settings of answering questions available as a bean.
 *
 * <h2>Why It Declares Nothing Else</h2>
 * The services of this package are components the container finds on its own. Binding a settings
 * record has to be asked for, and the module that is configurable asks for it, which keeps the
 * composition root free of knowledge about what this one happens to read.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatProperties.class)
public class ChatConfiguration {

    /**
     * Creates the configuration. The instance is created by the container and holds no state.
     */
    public ChatConfiguration() {
    }
}
