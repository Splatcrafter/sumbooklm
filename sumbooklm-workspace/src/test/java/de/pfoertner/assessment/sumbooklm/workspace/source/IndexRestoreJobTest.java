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

import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.persistence.document.SourceReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the run that rebuilds the retrieval index after a restart.
 *
 * <h2>Why It Must Not Stop</h2>
 * The index does not survive the process while the sources do, so every source is read again when
 * the application starts. One source that cannot be read must therefore not end the run, or a single
 * damaged row would leave every notebook of every user unanswerable until somebody noticed. The run
 * also reads from what is stored rather than from the network, which is what keeps a restart from
 * retrieving every page a deployment ever held.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class IndexRestoreJobTest {

    /**
     * Store the sources are listed from.
     */
    private SourceDocumentService sourceDocumentService;

    /**
     * Run one source is read by.
     */
    private SourceIngestionPipeline sourceIngestionPipeline;

    /**
     * Run under test.
     */
    private IndexRestoreJob job;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    IndexRestoreJobTest() {
    }

    /**
     * Builds the run and everything it reads through.
     */
    @BeforeEach
    void setUp() {
        this.sourceDocumentService = mock(SourceDocumentService.class);
        this.sourceIngestionPipeline = mock(SourceIngestionPipeline.class);
        this.job = new IndexRestoreJob(this.sourceDocumentService, this.sourceIngestionPipeline);
    }

    /**
     * Verifies that a deployment holding no source at all does nothing, which is what a fresh
     * installation starts as.
     */
    @Test
    void aDeploymentWithoutSourcesDoesNothing() {
        when(this.sourceDocumentService.references()).thenReturn(List.of());

        assertThat(this.job.restore()).isZero();
        verify(this.sourceIngestionPipeline, never()).index(any(), any(), anyBoolean());
    }

    /**
     * Verifies that every source is read again from what is stored rather than from where it came
     * from, so that a restart neither needs the network nor changes what a page said.
     */
    @Test
    void everySourceIsReadFromWhatIsStored() {
        final SourceReference first = reference();
        final SourceReference second = reference();
        when(this.sourceDocumentService.references()).thenReturn(List.of(first, second));
        when(this.sourceIngestionPipeline.index(any(), any(), anyBoolean())).thenReturn(true);

        assertThat(this.job.restore()).isEqualTo(2);
        verify(this.sourceIngestionPipeline).index(first.getUserId(), first.getId(), false);
        verify(this.sourceIngestionPipeline).index(second.getUserId(), second.getId(), false);
    }

    /**
     * Verifies that a source which could not be read does not end the run and is not counted, so
     * that one damaged row leaves the rest of a deployment answerable.
     */
    @Test
    void aSourceThatCannotBeReadDoesNotEndTheRun() {
        final SourceReference broken = reference();
        final SourceReference sound = reference();
        when(this.sourceDocumentService.references()).thenReturn(List.of(broken, sound));
        when(this.sourceIngestionPipeline.index(broken.getUserId(), broken.getId(), false))
                .thenReturn(false);
        when(this.sourceIngestionPipeline.index(sound.getUserId(), sound.getId(), false))
                .thenReturn(true);

        assertThat(this.job.restore()).isEqualTo(1);
        verify(this.sourceIngestionPipeline).index(sound.getUserId(), sound.getId(), false);
    }

    /**
     * Verifies that the run started when the application is ready does the same as one started by
     * hand, so that the two ways in cannot drift apart.
     */
    @Test
    void theRunAtStartupIsTheSameRun() {
        final SourceReference reference = reference();
        when(this.sourceDocumentService.references()).thenReturn(List.of(reference));
        when(this.sourceIngestionPipeline.index(any(), any(), anyBoolean())).thenReturn(true);

        this.job.onApplicationReady();

        verify(this.sourceIngestionPipeline).index(reference.getUserId(), reference.getId(), false);
    }

    /**
     * Builds one entry of the list of sources to read again.
     *
     * @return the entry naming a source and the account it belongs to
     */
    private static SourceReference reference() {
        final UUID id = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        return new SourceReference() {

            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public UUID getUserId() {
                return userId;
            }
        };
    }
}
