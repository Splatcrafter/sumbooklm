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

import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentFailure;
import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentStatus;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the stored part of a source and the stages it moves through.
 *
 * <h2>Why the Reason Is Cleared</h2>
 * A source that failed and is then read again must not keep the reason it failed with, because the
 * reason is what a user is shown and it would then describe a state the source has left. The
 * transitions below are where that clearing happens, and each of them is one of the four stages.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class DocumentPayloadTest {

    /**
     * Moment a source of the cases was read at.
     */
    private static final Instant READ_AT = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Payload of a source that failed, which most cases move on from.
     */
    private final DocumentPayload failed = new DocumentPayload("page.html", SourceKind.WEB,
            "https://example.org", DocumentStatus.ERROR, 0, DocumentFailure.UNREACHABLE, Instant.EPOCH);

    /**
     * Creates the test class.
     */
    DocumentPayloadTest() {
    }

    /**
     * Verifies that moving a source to another stage clears the reason it failed with, so that a
     * source being read again is not shown as one that failed.
     */
    @Test
    void movingToAnotherStageClearsTheReason() {
        final DocumentPayload indexing = this.failed.withStatus(DocumentStatus.INDEXING);

        assertThat(indexing.status()).isEqualTo(DocumentStatus.INDEXING);
        assertThat(indexing.failure()).isEqualTo(DocumentFailure.NONE);
        assertThat(indexing.origin()).isEqualTo("https://example.org");
        assertThat(indexing.kind()).isEqualTo(SourceKind.WEB);
    }

    /**
     * Verifies that recording a failure puts the source into the failed stage, so that the two
     * cannot be set apart from one another.
     */
    @Test
    void recordingAFailureAlsoSetsTheStage() {
        final DocumentPayload payload = new DocumentPayload("notes.txt", SourceKind.FILE, "notes.txt",
                DocumentStatus.INDEXING, 0, DocumentFailure.NONE, Instant.EPOCH);

        final DocumentPayload broken = payload.withFailure(DocumentFailure.UNREADABLE);

        assertThat(broken.status()).isEqualTo(DocumentStatus.ERROR);
        assertThat(broken.failure()).isEqualTo(DocumentFailure.UNREADABLE);
    }

    /**
     * Verifies that a source which was read carries its new name, its token count and the moment it
     * was read, and that it is no longer marked as failed.
     */
    @Test
    void aReadSourceCarriesItsResult() {
        final DocumentPayload ready = this.failed.withIndexingResult("Entropy explained", 512, READ_AT);

        assertThat(ready.displayName()).isEqualTo("Entropy explained");
        assertThat(ready.tokenCount()).isEqualTo(512);
        assertThat(ready.indexedAt()).isEqualTo(READ_AT);
        assertThat(ready.status()).isEqualTo(DocumentStatus.READY);
        assertThat(ready.failure()).isEqualTo(DocumentFailure.NONE);
    }

    /**
     * Verifies that the address a source came from survives every stage, because it is what the
     * source is read again from and what identifies it to the user.
     */
    @Test
    void theOriginSurvivesEveryStage() {
        assertThat(this.failed.withStatus(DocumentStatus.UPLOADED)
                .withFailure(DocumentFailure.BLOCKED)
                .withIndexingResult("Name", 1, READ_AT)
                .origin())
                .isEqualTo("https://example.org");
    }

    /**
     * Verifies that a source which was never read carries the beginning of time rather than nothing,
     * which is the value the mapper reads back as an absent moment.
     */
    @Test
    void aSourceThatWasNeverReadCarriesTheEpoch() {
        final DocumentPayload fresh = new DocumentPayload("notes.txt", SourceKind.FILE, "notes.txt",
                DocumentStatus.UPLOADED, 0, DocumentFailure.NONE, Instant.EPOCH);

        assertThat(fresh.indexedAt()).isEqualTo(Instant.EPOCH);
    }

    /**
     * Verifies that a negative number of tokens is refused, whether it is passed to the constructor
     * or to the transition that records a result.
     */
    @Test
    void aNegativeTokenCountIsRefused() {
        assertThatThrownBy(() -> new DocumentPayload("n", SourceKind.FILE, "o", DocumentStatus.READY,
                -1, DocumentFailure.NONE, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tokenCount");
        assertThatThrownBy(() -> this.failed.withIndexingResult("n", -5, READ_AT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tokenCount");
    }

    /**
     * Verifies that no field may be absent, because all of them are written into the stored payload.
     */
    @Test
    void noFieldMayBeAbsent() {
        assertThatThrownBy(() -> new DocumentPayload(null, SourceKind.FILE, "o", DocumentStatus.READY,
                0, DocumentFailure.NONE, Instant.EPOCH))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("displayName");
        assertThatThrownBy(() -> new DocumentPayload("n", null, "o", DocumentStatus.READY,
                0, DocumentFailure.NONE, Instant.EPOCH))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("kind");
        assertThatThrownBy(() -> new DocumentPayload("n", SourceKind.FILE, null, DocumentStatus.READY,
                0, DocumentFailure.NONE, Instant.EPOCH))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("origin");
        assertThatThrownBy(() -> new DocumentPayload("n", SourceKind.FILE, "o", null,
                0, DocumentFailure.NONE, Instant.EPOCH))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("status");
        assertThatThrownBy(() -> new DocumentPayload("n", SourceKind.FILE, "o", DocumentStatus.READY,
                0, null, Instant.EPOCH))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("failure");
        assertThatThrownBy(() -> new DocumentPayload("n", SourceKind.FILE, "o", DocumentStatus.READY,
                0, DocumentFailure.NONE, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("indexedAt");
    }
}
