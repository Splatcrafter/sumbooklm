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

package de.pfoertner.assessment.sumbooklm.persistence.user;

import de.pfoertner.assessment.sumbooklm.domain.user.AccountActivity;
import de.pfoertner.assessment.sumbooklm.domain.user.UserAccount;
import de.pfoertner.assessment.sumbooklm.domain.user.UserProfile;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadCodec;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadTypes;
import org.springframework.stereotype.Component;

/**
 * Assembles domain accounts from rows and payload bytes, and payload bytes from payload objects.
 *
 * <h2>Why This Exists</h2>
 * A user account is stored in two places at once: the columns of its row and the CBOR payload of
 * that row. Neither half is a complete account, and callers outside the persistence layer should
 * not have to know which half a field lives in. This component owns that split.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class UserAccountMapper {

    /**
     * Codec used to read and write the payload column of an account row.
     */
    private final PayloadCodec payloadCodec;

    /**
     * Creates the mapper.
     *
     * @param payloadCodec codec for the payload column of an account row
     */
    public UserAccountMapper(final PayloadCodec payloadCodec) {
        this.payloadCodec = payloadCodec;
    }

    /**
     * Decodes the payload of an account row.
     *
     * @param entity row to read the payload from
     * @return the decoded payload, migrated to the current payload schema version
     */
    public UserAccountPayload readPayload(final UserAccountEntity entity) {
        return this.payloadCodec.decode(
                PayloadTypes.USER_ACCOUNT, entity.getPayload(), entity.getPayloadVersion());
    }

    /**
     * Encodes a payload into the byte form stored in an account row.
     *
     * @param payload payload to encode
     * @return CBOR encoded payload at the current payload schema version
     */
    public byte[] writePayload(final UserAccountPayload payload) {
        return this.payloadCodec.encode(PayloadTypes.USER_ACCOUNT, payload);
    }

    /**
     * Combines a row and its payload into the domain representation of the account.
     *
     * @param entity row to convert
     * @return the account as the domain model describes it
     */
    public UserAccount toDomain(final UserAccountEntity entity) {
        final UserAccountPayload payload = readPayload(entity);
        return new UserAccount(
                entity.getId(),
                entity.getUsername(),
                new UserProfile(payload.firstName(), payload.lastName()),
                new AccountActivity(
                        entity.getRegisteredAt(),
                        payload.registrationIpAddress(),
                        entity.getLastLoginAt(),
                        payload.lastLoginIpAddress()));
    }
}
