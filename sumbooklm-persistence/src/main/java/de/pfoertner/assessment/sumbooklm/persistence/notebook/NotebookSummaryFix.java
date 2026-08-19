package de.pfoertner.assessment.sumbooklm.persistence.notebook;

import com.fasterxml.jackson.databind.JsonNode;
import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import de.splatgames.aether.datafixers.api.DataVersion;
import de.splatgames.aether.datafixers.api.TypeReference;
import de.splatgames.aether.datafixers.api.dynamic.Dynamic;
import de.splatgames.aether.datafixers.api.fix.DataFix;
import de.splatgames.aether.datafixers.api.fix.DataFixerContext;

/**
 * Carries a notebook payload of schema version {@code 1.0.0} to {@code 1.1.0} by giving it the two
 * fields the summary is stored in.
 *
 * <h2>Empty Rather Than Written</h2>
 * A notebook that existed before this version has no summary, and none can be produced here: writing
 * one is a request to a model, paid for by the user whose key it is. The fix therefore adds the
 * fields as empty, which is the same state a notebook created afterwards starts in, and leaves the
 * writing to the first reader who asks for it.
 *
 * <h2>Why the Tree and Not a Rule</h2>
 * The migration adds two constants to a record and touches nothing that exists, which is one
 * expression on the stored tree. Expressing it as a rewrite rule would mean naming the container type
 * and encoding a default through its codec to say the same thing.
 *
 * <h2>Idempotent</h2>
 * A field that is already present is left as it is. The pipeline applies a fix to data written at the
 * version it migrates from, so that cannot normally happen, but a fix that overwrote what it found
 * would turn a wrong version stamp into lost text rather than into a payload that survived it.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class NotebookSummaryFix implements DataFix<JsonNode> {

    /**
     * Name of the field the written summary is stored in.
     */
    private static final String SUMMARY_FIELD = "summary";

    /**
     * Name of the field the fingerprint of the summarised sources is stored in.
     */
    private static final String FINGERPRINT_FIELD = "summaryFingerprint";

    /**
     * Creates the fix. The instance holds no state and is applied to many payloads at once.
     */
    public NotebookSummaryFix() {
    }

    /**
     * Returns the name this fix is reported under.
     *
     * @return name of the fix
     */
    @Override
    public String name() {
        return "notebook_add_summary";
    }

    /**
     * Returns the version this fix migrates from.
     *
     * @return payload schema version {@code 1.0.0}
     */
    @Override
    public DataVersion fromVersion() {
        return new DataVersion(PayloadSchemaVersion.V1_0_0);
    }

    /**
     * Returns the version this fix migrates to.
     *
     * @return payload schema version {@code 1.1.0}
     */
    @Override
    public DataVersion toVersion() {
        return new DataVersion(PayloadSchemaVersion.V1_1_0);
    }

    /**
     * Adds the two summary fields to a stored notebook payload.
     *
     * @param type    reference of the payload being fixed, which is the notebook
     * @param input   stored payload as it was written at the earlier version
     * @param context context the fix may report to
     * @return the payload with an empty summary and an empty fingerprint
     */
    @Override
    public Dynamic<JsonNode> apply(final TypeReference type,
                                   final Dynamic<JsonNode> input,
                                   final DataFixerContext context) {
        Dynamic<JsonNode> fixed = input;
        if (!fixed.has(SUMMARY_FIELD)) {
            fixed = fixed.set(SUMMARY_FIELD, fixed.createString(""));
        }
        if (!fixed.has(FINGERPRINT_FIELD)) {
            fixed = fixed.set(FINGERPRINT_FIELD, fixed.createString(""));
        }
        return fixed;
    }
}
