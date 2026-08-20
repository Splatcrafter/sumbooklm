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
 * Exercises the rules a notebook is created under.
 *
 * <h2>Why It Is Tested Alone</h2>
 * The record is the shape every layer above the database hands around, and the only behaviour it
 * carries is what it refuses. Reaching those refusals through the application is impossible, because
 * the layers above never build one of the forbidden states on purpose; a defect that stopped them
 * from being refused would therefore surface far away from where it was introduced.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class NotebookTest {

    /**
     * Point in time every case is built with, chosen so that nothing depends on the current clock.
     */
    private static final Instant WHEN = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    NotebookTest() {
    }

    /**
     * Verifies that a notebook built from complete data keeps every value it was given.
     */
    @Test
    void completeDataIsKept() {
        final UUID id = UUID.randomUUID();
        final UUID owner = UUID.randomUUID();

        final Notebook notebook = new Notebook(id, owner, "Thermodynamics", true, "@", WHEN, WHEN, 4L);

        assertThat(notebook.id()).isEqualTo(id);
        assertThat(notebook.ownerId()).isEqualTo(owner);
        assertThat(notebook.title()).isEqualTo("Thermodynamics");
        assertThat(notebook.pinned()).isTrue();
        assertThat(notebook.topicIcon()).isEqualTo("@");
        assertThat(notebook.createdAt()).isEqualTo(WHEN);
        assertThat(notebook.lastActivityAt()).isEqualTo(WHEN);
        assertThat(notebook.sourceCount()).isEqualTo(4L);
    }

    /**
     * Verifies that a notebook without an identifier is refused, because such a notebook could
     * neither be read again nor written to.
     */
    @Test
    void anIdentifierIsRequired() {
        assertThatThrownBy(() -> new Notebook(null, UUID.randomUUID(), "T", false, "", WHEN, WHEN, 0L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
    }

    /**
     * Verifies that a notebook without an owner is refused, because the owner is the filter every
     * read and every write carries and a notebook without one would belong to everybody.
     */
    @Test
    void anOwnerIsRequired() {
        assertThatThrownBy(() -> new Notebook(UUID.randomUUID(), null, "T", false, "", WHEN, WHEN, 0L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ownerId");
    }

    /**
     * Verifies that the remaining references are required as well, so that no field of a notebook
     * can be absent where the presentation layer expects a value.
     */
    @Test
    void theRemainingReferencesAreRequired() {
        final UUID id = UUID.randomUUID();
        final UUID owner = UUID.randomUUID();

        assertThatThrownBy(() -> new Notebook(id, owner, null, false, "", WHEN, WHEN, 0L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("title");
        assertThatThrownBy(() -> new Notebook(id, owner, "T", false, null, WHEN, WHEN, 0L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("topicIcon");
        assertThatThrownBy(() -> new Notebook(id, owner, "T", false, "", null, WHEN, 0L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("createdAt");
        assertThatThrownBy(() -> new Notebook(id, owner, "T", false, "", WHEN, null, 0L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("lastActivityAt");
    }

    /**
     * Verifies that a negative number of sources is refused, which is the one value the count cannot
     * take and the one a broken query would produce.
     */
    @Test
    void aNegativeSourceCountIsRefused() {
        assertThatThrownBy(() -> new Notebook(
                UUID.randomUUID(), UUID.randomUUID(), "T", false, "", WHEN, WHEN, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceCount");
    }

    /**
     * Verifies that a notebook without sources is accepted, because that is the state every notebook
     * starts in and the boundary the refusal above sits next to.
     */
    @Test
    void aNotebookWithoutSourcesIsAccepted() {
        assertThatCode(() -> new Notebook(
                UUID.randomUUID(), UUID.randomUUID(), "T", false, "", WHEN, WHEN, 0L))
                .doesNotThrowAnyException();
    }

    /**
     * Verifies that an empty title and an empty icon are accepted, because a notebook may be created
     * before either is known and the layers above answer the emptiness themselves.
     */
    @Test
    void emptyTextIsAccepted() {
        final Notebook notebook = new Notebook(
                UUID.randomUUID(), UUID.randomUUID(), "", false, "", WHEN, WHEN, 0L);

        assertThat(notebook.title()).isEmpty();
        assertThat(notebook.topicIcon()).isEmpty();
    }

    /**
     * Verifies that two notebooks holding the same values are equal, which is what lets a caller
     * compare a notebook it read against one it expected.
     */
    @Test
    void notebooksOfEqualValueAreEqual() {
        final UUID id = UUID.randomUUID();
        final UUID owner = UUID.randomUUID();

        assertThat(new Notebook(id, owner, "T", false, "", WHEN, WHEN, 1L))
                .isEqualTo(new Notebook(id, owner, "T", false, "", WHEN, WHEN, 1L))
                .hasSameHashCodeAs(new Notebook(id, owner, "T", false, "", WHEN, WHEN, 1L));
    }
}
