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

package de.pfoertner.assessment.sumbooklm.domain.workspace;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the rules a source document is created under.
 *
 * <h2>The One Absent Field</h2>
 * Every reference of the record is required except the moment it was last read, which is absent for
 * as long as a source has never been read. That exception is the part worth stating, because a
 * constructor that required it would make an unread source impossible to describe.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class SourceDocumentTest {

    /**
     * Point in time every case is built with, chosen so that nothing depends on the current clock.
     */
    private static final Instant WHEN = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Identifier of the source under test.
     */
    private final UUID id = UUID.randomUUID();

    /**
     * Identifier of the notebook the source belongs to.
     */
    private final UUID notebookId = UUID.randomUUID();

    /**
     * Identifier of the account the source belongs to.
     */
    private final UUID ownerId = UUID.randomUUID();

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    SourceDocumentTest() {
    }

    /**
     * Verifies that a source which was never read carries no moment of reading, rather than being
     * refused for it.
     */
    @Test
    void aSourceThatWasNeverReadIsAccepted() {
        final SourceDocument source = new SourceDocument(this.id, this.notebookId, this.ownerId,
                "paper.pdf", SourceKind.FILE, "paper.pdf", DocumentStatus.UPLOADED, 0,
                DocumentFailure.NONE, null, WHEN);

        assertThat(source.indexedAt()).isNull();
        assertThat(source.status()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(source.failure()).isEqualTo(DocumentFailure.NONE);
    }

    /**
     * Verifies that every reference other than the moment of reading is required, so that no field a
     * list displays can be absent.
     */
    @Test
    void everyOtherReferenceIsRequired() {
        assertThatThrownBy(() -> source(null, this.notebookId, this.ownerId, "n", SourceKind.FILE,
                "o", DocumentStatus.READY, DocumentFailure.NONE, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("id");
        assertThatThrownBy(() -> source(this.id, null, this.ownerId, "n", SourceKind.FILE,
                "o", DocumentStatus.READY, DocumentFailure.NONE, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("notebookId");
        assertThatThrownBy(() -> source(this.id, this.notebookId, null, "n", SourceKind.FILE,
                "o", DocumentStatus.READY, DocumentFailure.NONE, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("ownerId");
        assertThatThrownBy(() -> source(this.id, this.notebookId, this.ownerId, null, SourceKind.FILE,
                "o", DocumentStatus.READY, DocumentFailure.NONE, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("displayName");
        assertThatThrownBy(() -> source(this.id, this.notebookId, this.ownerId, "n", null,
                "o", DocumentStatus.READY, DocumentFailure.NONE, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("kind");
        assertThatThrownBy(() -> source(this.id, this.notebookId, this.ownerId, "n", SourceKind.FILE,
                null, DocumentStatus.READY, DocumentFailure.NONE, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("origin");
        assertThatThrownBy(() -> source(this.id, this.notebookId, this.ownerId, "n", SourceKind.FILE,
                "o", null, DocumentFailure.NONE, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("status");
        assertThatThrownBy(() -> source(this.id, this.notebookId, this.ownerId, "n", SourceKind.FILE,
                "o", DocumentStatus.READY, null, WHEN))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("failure");
        assertThatThrownBy(() -> source(this.id, this.notebookId, this.ownerId, "n", SourceKind.FILE,
                "o", DocumentStatus.READY, DocumentFailure.NONE, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("createdAt");
    }

    /**
     * Verifies that a negative number of tokens is refused, which is the one value the count of an
     * embedding model cannot legitimately take.
     */
    @Test
    void aNegativeTokenCountIsRefused() {
        assertThatThrownBy(() -> new SourceDocument(this.id, this.notebookId, this.ownerId, "n",
                SourceKind.WEB, "https://example.org", DocumentStatus.READY, -1,
                DocumentFailure.NONE, WHEN, WHEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenCount");
    }

    /**
     * Verifies that a source counted as no tokens is accepted, because that is what a source carries
     * until indexing has finished.
     */
    @Test
    void aSourceWithoutTokensIsAccepted() {
        assertThatCode(() -> new SourceDocument(this.id, this.notebookId, this.ownerId, "n",
                SourceKind.WEB, "https://example.org", DocumentStatus.INDEXING, 0,
                DocumentFailure.NONE, null, WHEN))
                .doesNotThrowAnyException();
    }

    /**
     * Verifies that a source which failed carries both the stage and the reason, so that the two
     * together describe what a user is shown.
     */
    @Test
    void aFailedSourceCarriesItsReason() {
        final SourceDocument source = new SourceDocument(this.id, this.notebookId, this.ownerId,
                "https://example.org", SourceKind.WEB, "https://example.org", DocumentStatus.ERROR,
                0, DocumentFailure.BLOCKED, null, WHEN);

        assertThat(source.status()).isEqualTo(DocumentStatus.ERROR);
        assertThat(source.failure()).isEqualTo(DocumentFailure.BLOCKED);
    }

    /**
     * Builds a source from the arguments a case varies, with the values it does not vary fixed.
     *
     * @param id          stable identifier of the source
     * @param notebookId  identifier of the notebook the source belongs to
     * @param ownerId     identifier of the account the source belongs to
     * @param displayName name the source is listed under
     * @param kind        way the source entered the notebook
     * @param origin      name of the uploaded file or address of the page
     * @param status      stage the source has reached
     * @param failure     reason the source could not be indexed
     * @param createdAt   point in time the source was added to its notebook
     * @return the source built from the arguments
     */
    private static SourceDocument source(final UUID id,
                                         final UUID notebookId,
                                         final UUID ownerId,
                                         final String displayName,
                                         final SourceKind kind,
                                         final String origin,
                                         final DocumentStatus status,
                                         final DocumentFailure failure,
                                         final Instant createdAt) {
        return new SourceDocument(id, notebookId, ownerId, displayName, kind, origin, status, 0,
                failure, null, createdAt);
    }
}
