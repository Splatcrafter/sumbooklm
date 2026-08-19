package de.pfoertner.assessment.sumbooklm.workspace.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Externalized settings of answering questions, bound from the {@code sumbooklm.chat} namespace.
 *
 * <h2>Why the Rate Is Configured</h2>
 * How often an account may ask is a statement about what a deployment is willing to spend and to
 * serve, not about how the application works. Where the keys belong to the users, the value is there
 * to keep one client from occupying the installation; where an operator pays for anything behind the
 * same endpoint, it is the only thing between them and a script.
 *
 * @param questionsPerHour number of questions one account may ask within an hour
 * @author Erik Pförtner
 * @since 0.1.0
 */
@ConfigurationProperties("sumbooklm.chat")
public record ChatProperties(@DefaultValue("60") int questionsPerHour) {
}
