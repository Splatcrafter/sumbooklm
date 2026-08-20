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

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the constants stored data is written with.
 *
 * <h2>Why a Test Names Them</h2>
 * The four enumerations of this package are persisted by name. A constant that is renamed or removed
 * therefore makes every row carrying it unreadable, and nothing in the compiler notices, because the
 * name only appears in the database. Naming the constants here turns such a change into a failing
 * test rather than into a payload that cannot be decoded.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class WorkspaceEnumsTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    WorkspaceEnumsTest() {
    }

    /**
     * Verifies the authors a message may have been written by.
     */
    @Test
    void theAuthorsOfAMessageAreKnown() {
        assertThat(Arrays.stream(ChatRole.values()).map(Enum::name))
                .containsExactlyInAnyOrder("USER", "ASSISTANT");
    }

    /**
     * Verifies the stages a source passes through.
     */
    @Test
    void theStagesOfASourceAreKnown() {
        assertThat(Arrays.stream(DocumentStatus.values()).map(Enum::name))
                .containsExactlyInAnyOrder("UPLOADED", "INDEXING", "READY", "ERROR");
    }

    /**
     * Verifies the ways a source may have entered a notebook.
     */
    @Test
    void theWaysIntoANotebookAreKnown() {
        assertThat(Arrays.stream(SourceKind.values()).map(Enum::name))
                .containsExactlyInAnyOrder("FILE", "WEB");
    }

    /**
     * Verifies the reasons a source may have failed with, each of which stands for a different next
     * step a user can take.
     */
    @Test
    void theReasonsASourceFailedWithAreKnown() {
        assertThat(Arrays.stream(DocumentFailure.values()).map(Enum::name))
                .containsExactlyInAnyOrder("NONE", "BLOCKED", "UNREACHABLE", "UNREADABLE", "EMPTY",
                        "TOO_LARGE", "UNEXPECTED");
    }

    /**
     * Verifies that every stage of a source can be read back from its name, which is what the stored
     * rows are decoded by.
     *
     * @param status stage the case is run for
     */
    @ParameterizedTest
    @EnumSource(DocumentStatus.class)
    void everyStageSurvivesItsName(final DocumentStatus status) {
        assertThat(DocumentStatus.valueOf(status.name())).isSameAs(status);
    }

    /**
     * Verifies that every reason of a failure can be read back from its name.
     *
     * @param failure reason the case is run for
     */
    @ParameterizedTest
    @EnumSource(DocumentFailure.class)
    void everyReasonSurvivesItsName(final DocumentFailure failure) {
        assertThat(DocumentFailure.valueOf(failure.name())).isSameAs(failure);
    }
}
