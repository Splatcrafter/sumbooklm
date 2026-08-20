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

package de.pfoertner.assessment.sumbooklm.ai.embedding;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the keys a segment of the retrieval index is filtered by.
 *
 * <h2>Why the Keys Are Named</h2>
 * The two keys are written when a source is indexed and read when a question is answered. They are
 * strings on both sides, so a rename that reached only one of them would not fail to compile: it
 * would answer every question of every notebook with no passages at all, or with the passages of
 * somebody else.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class SegmentMetadataTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    SegmentMetadataTest() {
    }

    /**
     * Verifies the two keys every segment carries.
     */
    @Test
    void theKeysOfASegmentAreKnown() {
        assertThat(SegmentMetadata.NOTEBOOK_ID).isEqualTo("notebookId");
        assertThat(SegmentMetadata.SOURCE_DOCUMENT_ID).isEqualTo("sourceDocumentId");
    }

    /**
     * Verifies that the two keys differ, because a segment carries both and one would otherwise
     * overwrite the other.
     */
    @Test
    void theKeysDiffer() {
        assertThat(SegmentMetadata.NOTEBOOK_ID).isNotEqualTo(SegmentMetadata.SOURCE_DOCUMENT_ID);
    }

    /**
     * Verifies that the holder cannot be instantiated.
     *
     * @throws NoSuchMethodException if the constructor was removed
     */
    @Test
    void theHolderCannotBeInstantiated() throws NoSuchMethodException {
        final Constructor<SegmentMetadata> constructor = SegmentMetadata.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(AssertionError.class);
    }
}
