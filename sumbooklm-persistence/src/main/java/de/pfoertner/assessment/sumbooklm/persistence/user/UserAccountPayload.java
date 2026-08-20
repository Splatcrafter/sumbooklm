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

import java.util.Objects;

import de.splatgames.aether.datafixers.api.codec.Codec;
import de.splatgames.aether.datafixers.api.codec.Codecs;
import de.splatgames.aether.datafixers.api.codec.RecordCodecBuilder;

/**
 * Evolvable part of a user account as it is stored in the payload column.
 *
 * <h2>Boundary</h2>
 * The record holds the fields that describe a user without being part of the relational contract of
 * the account table. Adding a field here changes the payload schema and is handled by a data fixer;
 * adding a column to the table would require a schema migration instead.
 *
 * <h2>Network Addresses</h2>
 * The two address fields record where a request originated. They are stored as the textual form the
 * servlet container reported, without normalisation, because the value is audit information rather
 * than something the application resolves or compares.
 *
 * @param firstName             given name of the user
 * @param lastName              family name of the user
 * @param registrationIpAddress network address the registration was requested from
 * @param lastLoginIpAddress    network address of the most recent successful login
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record UserAccountPayload(String firstName,
                                 String lastName,
                                 String registrationIpAddress,
                                 String lastLoginIpAddress) {

    /**
     * Codec that maps the payload onto the format independent tree the migration pipeline operates
     * on. The field names below are part of the persisted format and must only be changed together
     * with a schema version and a data fix that performs the rename.
     */
    public static final Codec<UserAccountPayload> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codecs.STRING.fieldOf("firstName").forGetter(UserAccountPayload::firstName),
                    Codecs.STRING.fieldOf("lastName").forGetter(UserAccountPayload::lastName),
                    Codecs.STRING.fieldOf("registrationIpAddress").forGetter(UserAccountPayload::registrationIpAddress),
                    Codecs.STRING.fieldOf("lastLoginIpAddress").forGetter(UserAccountPayload::lastLoginIpAddress)
            ).apply(instance, UserAccountPayload::new));

    /**
     * Creates the payload.
     *
     * @param firstName             given name of the user
     * @param lastName              family name of the user
     * @param registrationIpAddress network address the registration was requested from
     * @param lastLoginIpAddress    network address of the most recent successful login
     * @throws NullPointerException if any argument is {@code null}
     */
    public UserAccountPayload {
        Objects.requireNonNull(firstName, "firstName must not be null");
        Objects.requireNonNull(lastName, "lastName must not be null");
        Objects.requireNonNull(registrationIpAddress, "registrationIpAddress must not be null");
        Objects.requireNonNull(lastLoginIpAddress, "lastLoginIpAddress must not be null");
    }
}
