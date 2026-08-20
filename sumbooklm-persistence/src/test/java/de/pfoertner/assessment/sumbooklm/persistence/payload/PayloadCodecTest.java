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
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import de.splatgames.aether.datafixers.api.DataVersion;
import de.splatgames.aether.datafixers.api.dynamic.Dynamic;
import de.splatgames.aether.datafixers.api.dynamic.TaggedDynamic;
import de.splatgames.aether.datafixers.codec.json.jackson.JacksonJsonOps;
import de.splatgames.aether.datafixers.core.AetherDataFixer;
import de.splatgames.aether.datafixers.spring.service.MigrationResult;
import de.splatgames.aether.datafixers.spring.service.MigrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises what happens to a payload that cannot be read.
 *
 * <h2>Why the Failures Are the Subject</h2>
 * The path where everything works is exercised by the application every time a notebook is opened,
 * and by the migration test of the application module end to end. What no running deployment
 * produces on purpose is a row whose bytes are damaged or whose version no migration leads out of,
 * and both of those have to end as a failure that names the type rather than as a parser error from
 * inside a library.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class PayloadCodecTest {

    /**
     * Reader the cases build stored bytes with.
     */
    private final CBORMapper cborMapper = CBORMapper.builder().build();

    /**
     * Fixer the codec encodes and decodes through.
     */
    private AetherDataFixer dataFixer;

    /**
     * Migration the codec runs a stored payload through.
     */
    private MigrationService migrationService;

    /**
     * Builder the migration is described with.
     */
    private MigrationService.MigrationRequestBuilder request;

    /**
     * Codec under test.
     */
    private PayloadCodec codec;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    PayloadCodecTest() {
    }

    /**
     * Builds the codec and the migration it asks.
     */
    @BeforeEach
    void setUp() {
        this.dataFixer = mock(AetherDataFixer.class);
        this.migrationService = mock(MigrationService.class);
        this.request = mock(MigrationService.MigrationRequestBuilder.class);
        this.codec = new PayloadCodec(this.dataFixer, this.migrationService);

        when(this.migrationService.migrate(any())).thenReturn(this.request);
        when(this.request.from(anyInt())).thenReturn(this.request);
        when(this.request.toLatest()).thenReturn(this.request);
    }

    /**
     * Verifies that bytes which are not a payload at all are refused with a failure that names the
     * type, so that a damaged row can be found from what the failure says.
     */
    @Test
    void bytesThatAreNotAPayloadAreRefused() {
        final byte[] truncated = {(byte) 0x82, (byte) 0x01};

        assertThatThrownBy(() ->
                this.codec.decode(PayloadTypes.NOTEBOOK, truncated, PayloadSchemaVersion.CURRENT))
                .isInstanceOf(PayloadCodecException.class)
                .hasMessageContaining("notebook")
                .hasCauseInstanceOf(IOException.class);
    }

    /**
     * Verifies that a payload no migration leads out of is refused with a failure that names both
     * the type and the version it was stored under, and that keeps what the migration reported.
     */
    @Test
    void aPayloadThatCannotBeMigratedIsRefused() {
        final Throwable reported = new IllegalStateException("no path from 90");
        when(this.request.execute()).thenReturn(MigrationResult.failure(
                new DataVersion(90), new DataVersion(PayloadSchemaVersion.CURRENT),
                "notebook", Duration.ZERO, reported));

        assertThatThrownBy(() -> this.codec.decode(PayloadTypes.NOTEBOOK, storedBytes(), 90))
                .isInstanceOf(PayloadCodecException.class)
                .hasMessageContaining("notebook")
                .hasMessageContaining("90")
                .hasCause(reported);
    }

    /**
     * Verifies that a stored payload is migrated from the version its row carries rather than from
     * the current one, because a row written by an older release is exactly what has to be lifted.
     */
    @Test
    void aStoredPayloadIsMigratedFromItsOwnVersion() {
        final TaggedDynamic migrated = new TaggedDynamic(
                PayloadTypes.NOTEBOOK, new Dynamic<>(new JacksonJsonOps(this.cborMapper), tree()));
        when(this.request.execute()).thenReturn(MigrationResult.success(migrated,
                new DataVersion(PayloadSchemaVersion.V1_0_0),
                new DataVersion(PayloadSchemaVersion.CURRENT), "notebook", Duration.ZERO));
        when(this.dataFixer.decode(any(), any())).thenReturn("decoded");

        assertThat(this.codec.<String>decode(
                PayloadTypes.NOTEBOOK, storedBytes(), PayloadSchemaVersion.V1_0_0))
                .isEqualTo("decoded");
        verify(this.request).from(PayloadSchemaVersion.V1_0_0);
        verify(this.request).toLatest();
    }

    /**
     * Verifies that a payload is written under the version this release stores, so that a row can be
     * migrated from what it says rather than from a guess.
     */
    @Test
    void aPayloadIsWrittenUnderTheCurrentVersion() {
        final TaggedDynamic encoded = new TaggedDynamic(
                PayloadTypes.NOTEBOOK, new Dynamic<>(new JacksonJsonOps(this.cborMapper), tree()));
        when(this.dataFixer.encode(any(), any(), any(), any())).thenReturn(encoded);

        final byte[] written = this.codec.encode(PayloadTypes.NOTEBOOK, "anything");

        assertThat(written).isNotEmpty();
        verify(this.dataFixer).encode(eq(new DataVersion(PayloadSchemaVersion.CURRENT)),
                eq(PayloadTypes.NOTEBOOK), eq("anything"), any());
    }

    /**
     * Builds a tree that stands for the content of a stored payload.
     *
     * @return a tree holding one field
     */
    private JsonNode tree() {
        final ObjectNode node = this.cborMapper.createObjectNode();
        node.put("title", "Thermodynamics");
        return node;
    }

    /**
     * Builds bytes that parse as a payload, whatever the case then does with them.
     *
     * @return the bytes of a stored payload
     */
    private byte[] storedBytes() {
        try {
            return this.cborMapper.writeValueAsBytes(tree());
        } catch (final IOException e) {
            throw new IllegalStateException("The bytes of the case cannot be written", e);
        }
    }
}
