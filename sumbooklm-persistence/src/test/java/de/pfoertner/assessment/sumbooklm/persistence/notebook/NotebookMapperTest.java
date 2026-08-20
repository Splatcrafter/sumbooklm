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

package de.pfoertner.assessment.sumbooklm.persistence.notebook;

import java.time.Instant;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.Notebook;
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
 * Exercises the step between a stored notebook and the record every layer above reads.
 *
 * <h2>The Count That Is Not Stored</h2>
 * How many sources a notebook holds is counted where it is asked for and handed to the mapper. That
 * is what keeps it from drifting away from the rows it describes, and it is also why the mapper
 * cannot be trusted to produce it: it has to be given, including the zero of a notebook that was
 * just created.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class NotebookMapperTest {

    /**
     * Moment the notebook of the cases was created at.
     */
    private static final Instant CREATED = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Moment the notebook of the cases was last used at.
     */
    private static final Instant ACTIVE = Instant.parse("2026-08-20T11:00:00Z");

    /**
     * Codec the mapper reads and writes payloads with.
     */
    private PayloadCodec payloadCodec;

    /**
     * Mapper under test.
     */
    private NotebookMapper mapper;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    NotebookMapperTest() {
    }

    /**
     * Builds the mapper and the codec it reads through.
     */
    @BeforeEach
    void setUp() {
        this.payloadCodec = mock(PayloadCodec.class);
        this.mapper = new NotebookMapper(this.payloadCodec);
    }

    /**
     * Verifies that a notebook is assembled from its row and its payload, with the count of sources
     * taken from the caller.
     */
    @Test
    void aNotebookIsAssembledFromRowPayloadAndCount() {
        final NotebookEntity entity = entity();
        final NotebookPayload payload =
                new NotebookPayload("Thermodynamics", true, "@", "About entropy.", "abc123");

        final Notebook notebook = this.mapper.toDomain(entity, payload, 3L);

        assertThat(notebook.id()).isEqualTo(entity.getId());
        assertThat(notebook.ownerId()).isEqualTo(entity.getUserId());
        assertThat(notebook.title()).isEqualTo("Thermodynamics");
        assertThat(notebook.pinned()).isTrue();
        assertThat(notebook.topicIcon()).isEqualTo("@");
        assertThat(notebook.createdAt()).isEqualTo(CREATED);
        assertThat(notebook.lastActivityAt()).isEqualTo(ACTIVE);
        assertThat(notebook.sourceCount()).isEqualTo(3L);
    }

    /**
     * Verifies that the summary of a notebook is not carried into the record, because every list of
     * notebooks would otherwise hand out a paragraph per entry that no list displays.
     */
    @Test
    void theSummaryIsNotCarriedIntoTheRecord() {
        final NotebookPayload payload =
                new NotebookPayload("Thermodynamics", false, "", "About entropy.", "abc123");

        final Notebook notebook = this.mapper.toDomain(entity(), payload, 0L);

        assertThat(notebook.toString()).doesNotContain("About entropy.").doesNotContain("abc123");
    }

    /**
     * Verifies that a notebook holding no sources is described with a count of zero rather than
     * being refused.
     */
    @Test
    void aNotebookWithoutSourcesIsCountedAsZero() {
        final NotebookPayload payload = new NotebookPayload("Fresh", false, "", "", "");

        assertThat(this.mapper.toDomain(entity(), payload, 0L).sourceCount()).isZero();
    }

    /**
     * Verifies that reading a notebook without a payload of its own decodes the stored bytes under
     * the version the row carries.
     */
    @Test
    void aStoredNotebookIsDecodedUnderItsOwnVersion() {
        final NotebookEntity entity = entity();
        when(this.payloadCodec.decode(eq(PayloadTypes.NOTEBOOK), any(), anyInt()))
                .thenReturn(new NotebookPayload("Stored", false, "", "", ""));

        assertThat(this.mapper.toDomain(entity, 1L).title()).isEqualTo("Stored");
        verify(this.payloadCodec).decode(
                PayloadTypes.NOTEBOOK, entity.getPayload(), entity.getPayloadVersion());
    }

    /**
     * Verifies that a payload is written under the name of its type.
     */
    @Test
    void aPayloadIsWrittenUnderItsType() {
        final NotebookPayload payload = new NotebookPayload("Thermodynamics", false, "", "", "");
        when(this.payloadCodec.encode(eq(PayloadTypes.NOTEBOOK), any())).thenReturn(new byte[]{9});

        assertThat(this.mapper.writePayload(payload)).containsExactly(9);
        verify(this.payloadCodec).encode(PayloadTypes.NOTEBOOK, payload);
    }

    /**
     * Builds the stored row the cases read from.
     *
     * @return a row of a notebook with a payload nothing in the case decodes
     */
    private static NotebookEntity entity() {
        return new NotebookEntity(UUID.randomUUID(), UUID.randomUUID(), CREATED, ACTIVE,
                new byte[]{1, 2}, PayloadSchemaVersion.CURRENT);
    }
}
