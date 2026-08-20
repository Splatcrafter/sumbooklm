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

import java.time.Instant;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.user.UserAccount;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadCodec;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadTypes;
import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the step between a stored account and the record every layer above reads.
 *
 * <h2>What May Not Cross</h2>
 * The row carries the password hash and the record does not. The case below states that by writing a
 * hash into the row and looking for it in everything the record can be asked for, because the record
 * is what a response is built from.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class UserAccountMapperTest {

    /**
     * Moment the account of the cases was registered at.
     */
    private static final Instant REGISTERED = Instant.parse("2026-01-02T03:04:05Z");

    /**
     * Moment the account of the cases was last used at.
     */
    private static final Instant LOGGED_IN = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Codec the mapper reads payloads with.
     */
    private PayloadCodec payloadCodec;

    /**
     * Mapper under test.
     */
    private UserAccountMapper mapper;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    UserAccountMapperTest() {
    }

    /**
     * Builds the mapper and the codec it reads through.
     */
    @BeforeEach
    void setUp() {
        this.payloadCodec = mock(PayloadCodec.class);
        this.mapper = new UserAccountMapper(this.payloadCodec);
    }

    /**
     * Verifies that an account is assembled from the row and the payload, with the name and the
     * addresses taken from the payload and the moments from the row.
     */
    @Test
    void anAccountIsAssembledFromRowAndPayload() {
        final UserAccountEntity entity = entity();
        when(this.payloadCodec.decode(eq(PayloadTypes.USER_ACCOUNT), any(), anyInt()))
                .thenReturn(new UserAccountPayload("Erik", "Pfoertner", "203.0.113.7", "198.51.100.4"));

        final UserAccount account = this.mapper.toDomain(entity);

        assertThat(account.id()).isEqualTo(entity.getId());
        assertThat(account.username()).isEqualTo("erik");
        assertThat(account.profile().firstName()).isEqualTo("Erik");
        assertThat(account.profile().lastName()).isEqualTo("Pfoertner");
        assertThat(account.activity().registeredAt()).isEqualTo(REGISTERED);
        assertThat(account.activity().lastLoginAt()).isEqualTo(LOGGED_IN);
        assertThat(account.activity().registrationIpAddress()).isEqualTo("203.0.113.7");
        assertThat(account.activity().lastLoginIpAddress()).isEqualTo("198.51.100.4");
    }

    /**
     * Verifies that the password hash of a row does not reach the record, because the record is what
     * a response carries.
     */
    @Test
    void thePasswordHashDoesNotCross() {
        final UserAccountEntity entity = entity();
        when(this.payloadCodec.decode(eq(PayloadTypes.USER_ACCOUNT), any(), anyInt()))
                .thenReturn(new UserAccountPayload("Erik", "Pfoertner", "203.0.113.7", "198.51.100.4"));

        final UserAccount account = this.mapper.toDomain(entity);

        assertThat(account.toString()).doesNotContain("$2a$10$secrethash");
    }

    /**
     * Verifies that the payload of an account is decoded under the version its row carries.
     */
    @Test
    void theStoredPayloadIsDecodedUnderItsOwnVersion() {
        final UserAccountEntity entity = entity();
        when(this.payloadCodec.decode(eq(PayloadTypes.USER_ACCOUNT), any(), anyInt()))
                .thenReturn(new UserAccountPayload("Erik", "Pfoertner", "a", "b"));

        this.mapper.toDomain(entity);

        verify(this.payloadCodec).decode(
                PayloadTypes.USER_ACCOUNT, entity.getPayload(), entity.getPayloadVersion());
    }

    /**
     * Verifies that a payload is written under the name of its type.
     */
    @Test
    void aPayloadIsWrittenUnderItsType() {
        final UserAccountPayload payload = new UserAccountPayload("Erik", "Pfoertner", "a", "b");
        when(this.payloadCodec.encode(eq(PayloadTypes.USER_ACCOUNT), any())).thenReturn(new byte[]{4});

        assertThat(this.mapper.writePayload(payload)).containsExactly(4);
        verify(this.payloadCodec).encode(PayloadTypes.USER_ACCOUNT, payload);
    }

    /**
     * Builds the stored row the cases read from.
     *
     * @return a row of an account carrying a password hash
     */
    private static UserAccountEntity entity() {
        return new UserAccountEntity(UUID.randomUUID(), "erik", "$2a$10$secrethash",
                REGISTERED, LOGGED_IN, new byte[]{1, 2}, PayloadSchemaVersion.CURRENT);
    }
}
