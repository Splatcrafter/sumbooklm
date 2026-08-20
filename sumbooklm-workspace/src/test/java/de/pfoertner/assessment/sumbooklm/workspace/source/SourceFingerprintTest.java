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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.persistence.document.SourceStamp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the values a source and a set of sources are recognised by.
 *
 * <h2>What Each Fingerprint Decides</h2>
 * The fingerprint of a file decides whether an upload is a duplicate, so two uploads of the same
 * bytes have to produce the same value and two different files must not. The fingerprint of an
 * address does the same for pages, where the same page can be written in several ways, so it is
 * taken from what the address means rather than from how it was typed. The fingerprint of a set
 * decides whether a stored summary still describes the notebook, so it has to change when a source
 * is added, removed or read again, and it must not change when the same sources are listed in
 * another order.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class SourceFingerprintTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    SourceFingerprintTest() {
    }

    /**
     * Verifies that the same bytes are recognised as the same file and different bytes are not.
     */
    @Test
    void theSameBytesAreTheSameFile() {
        final byte[] content = "Entropy never decreases.".getBytes(StandardCharsets.UTF_8);

        assertThat(SourceFingerprint.ofContent(content))
                .isEqualTo(SourceFingerprint.ofContent(content.clone()))
                .isNotEqualTo(SourceFingerprint.ofContent(
                        "Entropy never increases.".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Verifies that a file of no bytes still has a fingerprint, so that the duplicate check does not
     * have to answer a second question about emptiness.
     */
    @Test
    void anEmptyFileStillHasAFingerprint() {
        assertThat(SourceFingerprint.ofContent(new byte[0])).isNotEmpty().hasSize(64);
    }

    /**
     * Verifies that a difference of one byte produces a different fingerprint, which is what keeps
     * two versions of one document from being taken for each other.
     */
    @Test
    void oneByteOfDifferenceIsEnough() {
        assertThat(SourceFingerprint.ofContent(new byte[]{1, 2, 3}))
                .isNotEqualTo(SourceFingerprint.ofContent(new byte[]{1, 2, 4}));
    }

    /**
     * Verifies that the same page written in different ways is recognised as one page, because the
     * scheme and the host of an address mean the same whichever case they are written in.
     */
    @Test
    void theSamePageWrittenDifferentlyIsOnePage() {
        final String canonical = SourceFingerprint.ofAddress("https://example.org/article");

        assertThat(SourceFingerprint.ofAddress("HTTPS://EXAMPLE.ORG/article")).isEqualTo(canonical);
        assertThat(SourceFingerprint.ofAddress("  https://example.org/article  ")).isEqualTo(canonical);
    }

    /**
     * Verifies that the part of an address which selects a section of a page is not part of its
     * identity, because both addresses retrieve the same document.
     */
    @Test
    void theSectionOfAPageIsNotPartOfItsIdentity() {
        assertThat(SourceFingerprint.ofAddress("https://example.org/article#chapter-2"))
                .isEqualTo(SourceFingerprint.ofAddress("https://example.org/article"));
    }

    /**
     * Verifies that what the page is asked for is part of its identity, because two addresses that
     * differ in their query are two different documents.
     */
    @Test
    void theQueryIsPartOfTheIdentity() {
        assertThat(SourceFingerprint.ofAddress("https://example.org/search?q=entropy"))
                .isNotEqualTo(SourceFingerprint.ofAddress("https://example.org/search?q=heat"))
                .isNotEqualTo(SourceFingerprint.ofAddress("https://example.org/search"));
    }

    /**
     * Verifies that the port and the path are part of the identity, and that the protocol is too, so
     * that a page retrieved over another one is a source of its own.
     */
    @Test
    void thePortThePathAndTheProtocolAreAllPartOfTheIdentity() {
        assertThat(SourceFingerprint.ofAddress("https://example.org:8443/article"))
                .isNotEqualTo(SourceFingerprint.ofAddress("https://example.org/article"));
        assertThat(SourceFingerprint.ofAddress("https://example.org/other"))
                .isNotEqualTo(SourceFingerprint.ofAddress("https://example.org/article"));
        assertThat(SourceFingerprint.ofAddress("http://example.org/article"))
                .isNotEqualTo(SourceFingerprint.ofAddress("https://example.org/article"));
    }

    /**
     * Verifies that an address which cannot be read as one at all still produces a fingerprint,
     * because the duplicate check runs before anything decides whether the address can be retrieved.
     */
    @Test
    void anUnreadableAddressStillHasAFingerprint() {
        assertThat(SourceFingerprint.ofAddress("h ttp://not a url"))
                .isNotEmpty()
                .isEqualTo(SourceFingerprint.ofAddress("  h ttp://not a url  "));
    }

    /**
     * Verifies that a notebook without sources is described by nothing, which is what tells a
     * summary that was never written from one whose sources are gone.
     */
    @Test
    void aNotebookWithoutSourcesIsDescribedByNothing() {
        assertThat(SourceFingerprint.ofSourceSet(List.of())).isEmpty();
    }

    /**
     * Verifies that the order the sources are listed in does not change what the set is recognised
     * as, because a query may return them in any order.
     */
    @Test
    void theOrderOfTheSourcesDoesNotMatter() {
        final SourceStamp first = stamp(UUID.randomUUID(), "hash-a", 100);
        final SourceStamp second = stamp(UUID.randomUUID(), "hash-b", 200);

        assertThat(SourceFingerprint.ofSourceSet(List.of(first, second)))
                .isEqualTo(SourceFingerprint.ofSourceSet(List.of(second, first)));
    }

    /**
     * Verifies that adding a source changes what the set is recognised as, which is what marks a
     * stored summary as no longer describing the notebook.
     */
    @Test
    void addingASourceChangesTheSet() {
        final SourceStamp first = stamp(UUID.randomUUID(), "hash-a", 100);
        final SourceStamp second = stamp(UUID.randomUUID(), "hash-b", 200);

        assertThat(SourceFingerprint.ofSourceSet(List.of(first)))
                .isNotEqualTo(SourceFingerprint.ofSourceSet(List.of(first, second)));
    }

    /**
     * Verifies that a source whose text changed changes the set even though it is the same source,
     * which is what happens when a page is read again and says something else.
     */
    @Test
    void aSourceThatWasReadAgainChangesTheSet() {
        final UUID id = UUID.randomUUID();

        assertThat(SourceFingerprint.ofSourceSet(List.of(stamp(id, "hash-a", 100))))
                .isNotEqualTo(SourceFingerprint.ofSourceSet(List.of(stamp(id, "hash-a", 140))))
                .isNotEqualTo(SourceFingerprint.ofSourceSet(List.of(stamp(id, "hash-b", 100))));
    }

    /**
     * Verifies that replacing a source with another one changes the set, even where the number of
     * sources stays the same.
     */
    @Test
    void replacingASourceChangesTheSet() {
        final SourceStamp first = stamp(UUID.randomUUID(), "hash-a", 100);
        final SourceStamp second = stamp(UUID.randomUUID(), "hash-a", 100);

        assertThat(SourceFingerprint.ofSourceSet(List.of(first)))
                .isNotEqualTo(SourceFingerprint.ofSourceSet(List.of(second)));
    }

    /**
     * Verifies that the utility cannot be instantiated.
     *
     * @throws NoSuchMethodException if the constructor was removed
     */
    @Test
    void theUtilityCannotBeInstantiated() throws NoSuchMethodException {
        final Constructor<SourceFingerprint> constructor =
                SourceFingerprint.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(AssertionError.class);
    }

    /**
     * Builds one entry of the description of a set of sources.
     *
     * @param id           identifier of the source
     * @param documentHash fingerprint of what the source was added as
     * @param textLength   number of characters the source was read as
     * @return the entry describing that source
     */
    private static SourceStamp stamp(final UUID id, final String documentHash, final long textLength) {
        return new SourceStamp() {

            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public String getDocumentHash() {
                return documentHash;
            }

            @Override
            public long getTextLength() {
                return textLength;
            }
        };
    }
}
