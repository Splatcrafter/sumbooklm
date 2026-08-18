package de.pfoertner.assessment.sumbooklm.persistence.payload;

import de.splatgames.aether.datafixers.api.TypeReference;

/**
 * Identifiers of the payload kinds the application persists.
 *
 * <h2>Purpose</h2>
 * A type reference is the key under which a codec is registered in a schema and under which data
 * fixes are registered for migration. Both the write path and the read path have to name the same
 * reference for a payload to be encoded and migrated with the intended rules, which is why the
 * references are declared centrally instead of at their use sites.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class PayloadTypes {

    /**
     * Payload of a user account, holding the profile and the audit metadata of the account.
     */
    public static final TypeReference USER_ACCOUNT = new TypeReference("user_account");

    /**
     * Payload of a notebook, holding the title, the pin state and the topic icon.
     */
    public static final TypeReference NOTEBOOK = new TypeReference("notebook");

    /**
     * Payload of a source document, holding its name and everything the ingestion pipeline
     * determined about it.
     */
    public static final TypeReference SOURCE_DOCUMENT = new TypeReference("source_document");

    /**
     * Payload of a chat session, holding what a list of conversations displays about it.
     */
    public static final TypeReference CHAT_SESSION = new TypeReference("chat_session");

    /**
     * Prevents instantiation of this constant holder.
     */
    private PayloadTypes() {
        throw new AssertionError("PayloadTypes is a constant holder and must not be instantiated");
    }
}
