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

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import de.splatgames.aether.datafixers.api.DataVersion;
import de.splatgames.aether.datafixers.api.TypeReference;
import de.splatgames.aether.datafixers.api.dynamic.Dynamic;
import de.splatgames.aether.datafixers.api.dynamic.TaggedDynamic;
import de.splatgames.aether.datafixers.codec.json.jackson.JacksonJsonOps;
import de.splatgames.aether.datafixers.core.AetherDataFixer;
import de.splatgames.aether.datafixers.spring.service.MigrationResult;
import de.splatgames.aether.datafixers.spring.service.MigrationService;
import org.springframework.stereotype.Component;

/**
 * Translates between payload objects and the CBOR bytes stored in the payload column.
 *
 * <h2>Write Path</h2>
 * A payload is encoded with the codec registered for its type reference in the schema of
 * {@link PayloadSchemaVersion#CURRENT}, which yields a format independent tree, and the tree is
 * serialized to CBOR. The caller stores the returned bytes together with
 * {@link PayloadSchemaVersion#CURRENT}.
 *
 * <h2>Read Path</h2>
 * Stored bytes are parsed back into a tree, the tree is handed to the migration pipeline for the
 * range between the version it was written with and the current one, and the migrated tree is
 * decoded with the codec of the current schema. A payload already written at the current version
 * passes the pipeline unchanged.
 *
 * <h2>Jackson Generation</h2>
 * The mapper is a Jackson 2 {@code CBORMapper} rather than the Jackson 3 mapper of the web layer,
 * because the Aether Datafixers codec module is compiled against Jackson 2. It is created here
 * instead of being published as a bean so that it cannot be picked up as the application wide
 * {@code ObjectMapper} of either generation.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class PayloadCodec {

    /**
     * Serializer of the format independent tree into the CBOR bytes that reach the database.
     */
    private final CBORMapper cborMapper = CBORMapper.builder().build();

    /**
     * Bridge between the migration pipeline and the Jackson tree the CBOR mapper produces.
     */
    private final JacksonJsonOps ops = new JacksonJsonOps(this.cborMapper);

    /**
     * Data fixer holding the schemas, used to encode and decode with the codec of a given version.
     */
    private final AetherDataFixer dataFixer;

    /**
     * Migration pipeline applied to a payload whose stored version is behind the current one.
     */
    private final MigrationService migrationService;

    /**
     * Creates the codec.
     *
     * @param dataFixer        data fixer assembled from {@link PayloadDataFixerBootstrap}
     * @param migrationService migration pipeline published by the Aether Datafixers starter
     */
    public PayloadCodec(final AetherDataFixer dataFixer, final MigrationService migrationService) {
        this.dataFixer = dataFixer;
        this.migrationService = migrationService;
    }

    /**
     * Encodes a payload into the CBOR bytes of the current payload schema version.
     *
     * @param type    reference the payload is registered under in the current schema
     * @param payload payload object to encode
     * @param <A>     type of the payload object
     * @return CBOR encoded payload, written at {@link PayloadSchemaVersion#CURRENT}
     * @throws PayloadCodecException if the payload cannot be encoded or serialized
     */
    public <A> byte[] encode(final TypeReference type, final A payload) {
        final TaggedDynamic encoded = this.dataFixer.encode(
                new DataVersion(PayloadSchemaVersion.CURRENT), type, payload, this.ops);
        try {
            return this.cborMapper.writeValueAsBytes(encoded.value().value());
        } catch (final IOException e) {
            throw new PayloadCodecException("Cannot serialize payload of type " + type.getId(), e);
        }
    }

    /**
     * Decodes stored CBOR bytes, migrating them to the current payload schema version first.
     *
     * @param type          reference the payload is registered under
     * @param payload       CBOR encoded payload as it was read from the database
     * @param storedVersion payload schema version the bytes were written with
     * @param <A>           type of the payload object
     * @return the decoded payload at the current schema version
     * @throws PayloadCodecException if the bytes cannot be parsed, migrated or decoded
     */
    public <A> A decode(final TypeReference type, final byte[] payload, final int storedVersion) {
        final JsonNode tree;
        try {
            tree = this.cborMapper.readTree(payload);
        } catch (final IOException e) {
            throw new PayloadCodecException("Cannot parse stored payload of type " + type.getId(), e);
        }

        final TaggedDynamic stored = new TaggedDynamic(type, new Dynamic<>(this.ops, tree));
        final MigrationResult migration = this.migrationService.migrate(stored)
                .from(storedVersion)
                .toLatest()
                .execute();
        if (migration.isFailure()) {
            throw new PayloadCodecException(
                    "Cannot migrate payload of type " + type.getId() + " from schema version " + storedVersion,
                    migration.getError().orElse(null));
        }

        return this.dataFixer.decode(new DataVersion(PayloadSchemaVersion.CURRENT), migration.getData());
    }
}
