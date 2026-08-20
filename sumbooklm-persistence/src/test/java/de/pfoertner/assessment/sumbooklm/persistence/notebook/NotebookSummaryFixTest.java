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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadTypes;
import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import de.splatgames.aether.datafixers.api.dynamic.Dynamic;
import de.splatgames.aether.datafixers.codec.json.jackson.JacksonJsonOps;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the fix that lifts a notebook written before summaries existed.
 *
 * <h2>Why It Is Tested Without a Database</h2>
 * The fix is what stands between a deployment that is updated and the notebooks its users already
 * have. It runs on a tree rather than on a row, so it can be stated exactly: a payload from before
 * gains the two fields, a payload that already has them keeps what it says, and nothing else is
 * touched. Reaching the same statement through the application would mean writing an old row by
 * hand and reading it back, which says less and takes a schema to do.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class NotebookSummaryFixTest {

    /**
     * Reader the trees of the cases are built with.
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Fix under test.
     */
    private final NotebookSummaryFix fix = new NotebookSummaryFix();

    /**
     * Creates the test class.
     */
    NotebookSummaryFixTest() {
    }

    /**
     * Verifies that the fix leads from the schema notebooks were written under before summaries
     * existed to the one they are written under now.
     */
    @Test
    void theFixLeadsFromTheOldSchemaToTheCurrentOne() {
        assertThat(this.fix.fromVersion().getVersion()).isEqualTo(PayloadSchemaVersion.V1_0_0);
        assertThat(this.fix.toVersion().getVersion()).isEqualTo(PayloadSchemaVersion.V1_1_0);
        assertThat(this.fix.name()).isEqualTo("notebook_add_summary");
    }

    /**
     * Verifies that a notebook written before summaries existed gains both fields as empty text,
     * which is the state the layers above read as "nothing was written yet".
     */
    @Test
    void anOldNotebookGainsBothFields() {
        final ObjectNode old = this.mapper.createObjectNode();
        old.put("title", "Thermodynamics");
        old.put("pinned", true);
        old.put("topicIcon", "@");

        final JsonNode fixed = apply(old);

        assertThat(fixed.get("summary").asText()).isEmpty();
        assertThat(fixed.get("summaryFingerprint").asText()).isEmpty();
    }

    /**
     * Verifies that the fields a notebook already carries are left as they are, so that running the
     * fix twice cannot erase a summary that was already written.
     */
    @Test
    void anExistingSummarySurvivesTheFix() {
        final ObjectNode stored = this.mapper.createObjectNode();
        stored.put("title", "Thermodynamics");
        stored.put("pinned", false);
        stored.put("topicIcon", "");
        stored.put("summary", "About entropy.");
        stored.put("summaryFingerprint", "abc123");

        final JsonNode fixed = apply(stored);

        assertThat(fixed.get("summary").asText()).isEqualTo("About entropy.");
        assertThat(fixed.get("summaryFingerprint").asText()).isEqualTo("abc123");
    }

    /**
     * Verifies that a notebook carrying one of the two fields gains only the other, which is the
     * half migrated state a run that was interrupted would leave behind.
     */
    @Test
    void aHalfMigratedNotebookGainsOnlyWhatIsMissing() {
        final ObjectNode half = this.mapper.createObjectNode();
        half.put("title", "Thermodynamics");
        half.put("pinned", false);
        half.put("topicIcon", "");
        half.put("summary", "About entropy.");

        final JsonNode fixed = apply(half);

        assertThat(fixed.get("summary").asText()).isEqualTo("About entropy.");
        assertThat(fixed.get("summaryFingerprint").asText()).isEmpty();
    }

    /**
     * Verifies that the fields a notebook was already described by are untouched, because the fix
     * adds and never rewrites.
     */
    @Test
    void theRemainingFieldsAreUntouched() {
        final ObjectNode old = this.mapper.createObjectNode();
        old.put("title", "Thermodynamics");
        old.put("pinned", true);
        old.put("topicIcon", "@");

        final JsonNode fixed = apply(old);

        assertThat(fixed.get("title").asText()).isEqualTo("Thermodynamics");
        assertThat(fixed.get("pinned").asBoolean()).isTrue();
        assertThat(fixed.get("topicIcon").asText()).isEqualTo("@");
    }

    /**
     * Runs the fix over one tree.
     *
     * @param payload tree of a stored notebook
     * @return the tree the fix produced
     */
    private JsonNode apply(final JsonNode payload) {
        return this.fix.apply(PayloadTypes.NOTEBOOK,
                new Dynamic<>(new JacksonJsonOps(this.mapper), payload), null).value();
    }
}
