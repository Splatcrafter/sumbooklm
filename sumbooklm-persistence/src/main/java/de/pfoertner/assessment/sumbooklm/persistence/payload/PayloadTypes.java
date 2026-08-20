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
