package de.pfoertner.assessment.sumbooklm.api.support;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Reads the account an access token was issued for.
 *
 * <h2>Why a Component</h2>
 * Every endpoint that works on data of one account needs the same value out of the same claim. Doing
 * that in each controller would spread the knowledge that the subject of a token is an account
 * identifier across the transport layer, and would leave each of them to decide what a token without
 * a usable subject means.
 *
 * <h2>Unusable Tokens</h2>
 * A token that carries no subject, or one that is not an identifier, is rejected as an invalid
 * bearer token rather than treated as an unknown account. Such a token was not issued by this
 * application, and answering with an authorization failure is what tells the client to obtain a new
 * one.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class AuthenticatedUserResolver {

    /**
     * Creates the resolver. The instance is created by the container and holds no state.
     */
    public AuthenticatedUserResolver() {
    }

    /**
     * Returns the account the given access token was issued for.
     *
     * @param accessToken verified access token of the caller
     * @return identifier of the account named by the subject of the token
     * @throws InvalidBearerTokenException if the token carries no subject or one that is not an
     *                                     identifier
     */
    public UUID requireUserId(final Jwt accessToken) {
        final String subject = accessToken.getSubject();
        if (subject == null) {
            throw new InvalidBearerTokenException("The access token carries no subject");
        }
        try {
            return UUID.fromString(subject);
        } catch (final IllegalArgumentException e) {
            throw new InvalidBearerTokenException("The subject of the access token is not an account identifier");
        }
    }
}
