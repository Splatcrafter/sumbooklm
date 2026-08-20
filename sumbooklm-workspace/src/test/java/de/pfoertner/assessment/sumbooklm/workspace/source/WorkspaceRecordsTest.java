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

package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceKind;
import de.pfoertner.assessment.sumbooklm.workspace.notebook.NotebookRemovedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the small records the pipeline is driven by.
 *
 * <h2>Why They Are Stated Together</h2>
 * Each of them is what one step of ingestion hands to the next, and all of them are used on a thread
 * that can no longer ask anybody. A missing field there would surface as a failure inside an
 * asynchronous run rather than at the point it was built, which is why the constructors refuse
 * rather than tolerate. The one field that may be absent, the bytes of an uploaded file, is absent
 * for a page and for a source read from what was stored, so it is a value rather than a gap.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class WorkspaceRecordsTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    WorkspaceRecordsTest() {
    }

    /**
     * Verifies that the input of a run may carry neither bytes nor a stored text, which is what a
     * page that was never read looks like.
     */
    @Test
    void theInputOfARunMayCarryNeitherBytesNorText() {
        final IngestionInput input = new IngestionInput(UUID.randomUUID(), SourceKind.WEB,
                "https://example.org", "Page", null, null);

        assertThat(input.content()).isNull();
        assertThat(input.extractedText()).isNull();
        assertThat(input.origin()).isEqualTo("https://example.org");
    }

    /**
     * Verifies that the input of a run states which notebook, which kind, which origin and which
     * name it belongs to, and refuses to be built without any of them.
     */
    @Test
    void theInputOfARunNamesWhereItBelongs() {
        final UUID notebookId = UUID.randomUUID();

        assertThatThrownBy(() ->
                new IngestionInput(null, SourceKind.FILE, "o", "n", null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("notebookId");
        assertThatThrownBy(() ->
                new IngestionInput(notebookId, null, "o", "n", null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("kind");
        assertThatThrownBy(() ->
                new IngestionInput(notebookId, SourceKind.FILE, null, "n", null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("origin");
        assertThatThrownBy(() ->
                new IngestionInput(notebookId, SourceKind.FILE, "o", null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("displayName");
    }

    /**
     * Verifies that the text of a source states which source it came from and under which name, so
     * that a summary can name what it describes.
     */
    @Test
    void theTextOfASourceNamesItsSource() {
        final UUID id = UUID.randomUUID();

        final SourceText text = new SourceText(id, "Thermodynamics.pdf", "Entropy never decreases.");

        assertThat(text.id()).isEqualTo(id);
        assertThat(text.displayName()).isEqualTo("Thermodynamics.pdf");
        assertThatThrownBy(() -> new SourceText(null, "n", "t"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("id");
        assertThatThrownBy(() -> new SourceText(id, null, "t"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("displayName");
        assertThatThrownBy(() -> new SourceText(id, "n", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("text");
    }

    /**
     * Verifies that an announced run names the account as well as the source, because the run reads
     * and writes under that account and has nobody to ask for it.
     */
    @Test
    void anAnnouncedRunNamesTheAccountAndTheSource() {
        final UUID userId = UUID.randomUUID();
        final UUID sourceId = UUID.randomUUID();

        final SourceIndexRequestedEvent event = new SourceIndexRequestedEvent(userId, sourceId, true);

        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.sourceId()).isEqualTo(sourceId);
        assertThat(event.reread()).isTrue();
        assertThatThrownBy(() -> new SourceIndexRequestedEvent(null, sourceId, false))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("userId");
        assertThatThrownBy(() -> new SourceIndexRequestedEvent(userId, null, false))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("sourceId");
    }

    /**
     * Verifies that the two removals name what was removed and refuse to be built without it,
     * because both are read by the listener that clears the retrieval index.
     */
    @Test
    void theRemovalsNameWhatWasRemoved() {
        final UUID sourceId = UUID.randomUUID();
        final UUID notebookId = UUID.randomUUID();

        assertThat(new SourceRemovedEvent(sourceId).sourceId()).isEqualTo(sourceId);
        assertThat(new NotebookRemovedEvent(notebookId).notebookId()).isEqualTo(notebookId);
        assertThatThrownBy(() -> new SourceRemovedEvent(null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("sourceId");
        assertThatThrownBy(() -> new NotebookRemovedEvent(null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("notebookId");
    }

    /**
     * Verifies that the failures of this package say which notebook or which source they are about,
     * because they are answered with a status that says nothing on its own.
     */
    @Test
    void theFailuresNameWhatTheyAreAbout() {
        final UUID notebookId = UUID.randomUUID();
        final UUID sourceId = UUID.randomUUID();

        assertThat(new DuplicateSourceException(notebookId)).hasMessageContaining(notebookId.toString());
        assertThat(new SourceNotFoundException(sourceId)).hasMessageContaining(sourceId.toString());
        assertThat(new EmptyUploadException("notes.txt")).hasMessageContaining("notes.txt");
    }

    /**
     * Verifies that a source removed at the moment it is announced can still be described, because
     * the identity is all the listener needs and it has already been removed by then.
     */
    @Test
    void anAnnouncementNeedsNothingButTheIdentity() {
        assertThatCode(() -> new SourceRemovedEvent(UUID.randomUUID())).doesNotThrowAnyException();
    }
}
