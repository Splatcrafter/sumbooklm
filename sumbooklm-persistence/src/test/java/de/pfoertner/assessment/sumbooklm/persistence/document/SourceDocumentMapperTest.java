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

package de.pfoertner.assessment.sumbooklm.persistence.document;

import java.time.Instant;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentFailure;
import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentStatus;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceDocument;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceKind;
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
 * Exercises the step between a stored source and the record every layer above reads.
 *
 * <h2>The Moment That Is Not Stored</h2>
 * A source that was never read is stored as the beginning of time, because the payload writes a
 * moment and has no absent state, while the record above says the same thing with nothing at all.
 * The translation between the two lives here, and it is the one place where a source that was read
 * in 1970 would be indistinguishable from one that was never read.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class SourceDocumentMapperTest {

    /**
     * Moment the source of the cases was created at.
     */
    private static final Instant CREATED = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Moment the source of the cases was read at.
     */
    private static final Instant READ = Instant.parse("2026-08-20T11:00:00Z");

    /**
     * Codec the mapper reads and writes payloads with.
     */
    private PayloadCodec payloadCodec;

    /**
     * Mapper under test.
     */
    private SourceDocumentMapper mapper;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    SourceDocumentMapperTest() {
    }

    /**
     * Builds the mapper and the codec it reads through.
     */
    @BeforeEach
    void setUp() {
        this.payloadCodec = mock(PayloadCodec.class);
        this.mapper = new SourceDocumentMapper(this.payloadCodec);
    }

    /**
     * Verifies that a source which was read carries the moment it was read at.
     */
    @Test
    void aSourceThatWasReadCarriesItsMoment() {
        final SourceDocumentEntity entity = entity();
        final DocumentPayload payload = new DocumentPayload("Entropy explained", SourceKind.WEB,
                "https://example.org", DocumentStatus.READY, 512, DocumentFailure.NONE, READ);

        final SourceDocument source = this.mapper.toDomain(entity, payload);

        assertThat(source.indexedAt()).isEqualTo(READ);
        assertThat(source.displayName()).isEqualTo("Entropy explained");
        assertThat(source.tokenCount()).isEqualTo(512);
        assertThat(source.status()).isEqualTo(DocumentStatus.READY);
        assertThat(source.kind()).isEqualTo(SourceKind.WEB);
        assertThat(source.origin()).isEqualTo("https://example.org");
    }

    /**
     * Verifies that a source which was never read carries no moment at all, so that the presentation
     * layer can say it was never read instead of dating it to 1970.
     */
    @Test
    void aSourceThatWasNeverReadCarriesNoMoment() {
        final DocumentPayload payload = new DocumentPayload("notes.txt", SourceKind.FILE, "notes.txt",
                DocumentStatus.UPLOADED, 0, DocumentFailure.NONE, Instant.EPOCH);

        assertThat(this.mapper.toDomain(entity(), payload).indexedAt()).isNull();
    }

    /**
     * Verifies that the identifiers of a source come from the row rather than from the payload,
     * because they are what a query filters on and must not be writable through a payload.
     */
    @Test
    void theIdentifiersComeFromTheRow() {
        final SourceDocumentEntity entity = entity();
        final DocumentPayload payload = new DocumentPayload("notes.txt", SourceKind.FILE, "notes.txt",
                DocumentStatus.READY, 1, DocumentFailure.NONE, READ);

        final SourceDocument source = this.mapper.toDomain(entity, payload);

        assertThat(source.id()).isEqualTo(entity.getId());
        assertThat(source.notebookId()).isEqualTo(entity.getNotebookId());
        assertThat(source.ownerId()).isEqualTo(entity.getUserId());
        assertThat(source.createdAt()).isEqualTo(entity.getCreatedAt());
    }

    /**
     * Verifies that reading a source without a payload of its own decodes the stored bytes under the
     * version the row carries, which is what lets an old row be read at all.
     */
    @Test
    void aStoredSourceIsDecodedUnderItsOwnVersion() {
        final SourceDocumentEntity entity = entity();
        final DocumentPayload payload = new DocumentPayload("notes.txt", SourceKind.FILE, "notes.txt",
                DocumentStatus.ERROR, 0, DocumentFailure.TOO_LARGE, Instant.EPOCH);
        when(this.payloadCodec.decode(eq(PayloadTypes.SOURCE_DOCUMENT), any(), anyInt()))
                .thenReturn(payload);

        final SourceDocument source = this.mapper.toDomain(entity);

        assertThat(source.failure()).isEqualTo(DocumentFailure.TOO_LARGE);
        verify(this.payloadCodec).decode(
                PayloadTypes.SOURCE_DOCUMENT, entity.getPayload(), entity.getPayloadVersion());
    }

    /**
     * Verifies that a payload is written under the name of its type, so that it is decoded by the
     * codec that wrote it.
     */
    @Test
    void aPayloadIsWrittenUnderItsType() {
        final DocumentPayload payload = new DocumentPayload("notes.txt", SourceKind.FILE, "notes.txt",
                DocumentStatus.READY, 1, DocumentFailure.NONE, READ);
        when(this.payloadCodec.encode(eq(PayloadTypes.SOURCE_DOCUMENT), any()))
                .thenReturn(new byte[]{7, 8});

        assertThat(this.mapper.writePayload(payload)).containsExactly(7, 8);
        verify(this.payloadCodec).encode(PayloadTypes.SOURCE_DOCUMENT, payload);
    }

    /**
     * Builds the stored row the cases read from.
     *
     * @return a row of a source with a payload nothing in the case decodes
     */
    private static SourceDocumentEntity entity() {
        return new SourceDocumentEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                CREATED, "hash", new byte[]{1, 2}, new byte[]{3, 4}, PayloadSchemaVersion.CURRENT);
    }
}
