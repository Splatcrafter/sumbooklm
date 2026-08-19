package de.pfoertner.assessment.sumbooklm.persistence.notebook;

import java.util.Objects;

import de.splatgames.aether.datafixers.api.codec.Codec;
import de.splatgames.aether.datafixers.api.codec.Codecs;
import de.splatgames.aether.datafixers.api.codec.RecordCodecBuilder;

/**
 * Evolvable part of a notebook as it is stored in the payload column.
 *
 * <h2>Boundary</h2>
 * The record holds the fields a user edits. None of them is part of the relational contract of the
 * notebook table, which is what allows a new field to be introduced by a data fixer instead of by a
 * schema migration.
 *
 * <h2>Pin State</h2>
 * The pin state lives here rather than in a column, so the overview reads every notebook of the
 * account and groups them afterwards instead of asking the database for two sets. The cost is that
 * the grouping cannot be pushed down into a query; the number of notebooks one account holds keeps
 * that irrelevant, and the alternative would have made the first user visible flag the first reason
 * to migrate a table.
 *
 * <h2>Topic Icon</h2>
 * The icon is stored as the string it is displayed as, not as a code point, because a symbol that
 * stands for a subject is frequently a sequence rather than a single character. It is empty until it
 * has been derived from the content of the notebook.
 *
 * <h2>Summary</h2>
 * The summary is the text a model wrote about the sources of the notebook, empty while none has been
 * written. Next to it stands the fingerprint of the sources it was written from, which is what lets a
 * later read say that the summary describes a set of sources the notebook no longer holds. The two
 * are always written together, because a summary without the fingerprint of its material could only
 * ever be read as current.
 *
 * @param title              name the user gave the notebook
 * @param pinned             whether the user pinned the notebook to the top of their overview
 * @param topicIcon          characters standing for the subject of the notebook, empty while unknown
 * @param summary            text a model wrote about the sources, empty while none was written
 * @param summaryFingerprint fingerprint of the sources the summary was written from, empty with it
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record NotebookPayload(String title,
                              boolean pinned,
                              String topicIcon,
                              String summary,
                              String summaryFingerprint) {

    /**
     * Codec that maps the payload onto the format independent tree the migration pipeline operates
     * on. The field names below are part of the persisted format and must only be changed together
     * with a schema version and a data fix that performs the rename.
     */
    public static final Codec<NotebookPayload> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codecs.STRING.fieldOf("title").forGetter(NotebookPayload::title),
                    Codecs.BOOL.fieldOf("pinned").forGetter(NotebookPayload::pinned),
                    Codecs.STRING.fieldOf("topicIcon").forGetter(NotebookPayload::topicIcon),
                    Codecs.STRING.fieldOf("summary").forGetter(NotebookPayload::summary),
                    Codecs.STRING.fieldOf("summaryFingerprint").forGetter(NotebookPayload::summaryFingerprint)
            ).apply(instance, NotebookPayload::new));

    /**
     * Codec of the shape this payload had at schema version {@code 1.0.0}, which knew nothing of a
     * summary.
     *
     * <p>It is registered for that version so that a payload written then is read with the codec it
     * was written with. The two summary fields are supplied as empty on the way in and dropped on the
     * way out, which is what keeps the earlier shape describable by the record that has since grown
     * past it. Carrying data forward is not its job: that is what {@link NotebookSummaryFix} does.
     */
    public static final Codec<NotebookPayload> CODEC_V1_0_0 = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codecs.STRING.fieldOf("title").forGetter(NotebookPayload::title),
                    Codecs.BOOL.fieldOf("pinned").forGetter(NotebookPayload::pinned),
                    Codecs.STRING.fieldOf("topicIcon").forGetter(NotebookPayload::topicIcon)
            ).apply(instance, (title, pinned, topicIcon) ->
                    new NotebookPayload(title, pinned, topicIcon, "", "")));

    /**
     * Creates the payload.
     *
     * @param title              name the user gave the notebook
     * @param pinned             whether the user pinned the notebook to the top of their overview
     * @param topicIcon          characters standing for the subject of the notebook, empty while
     *                           unknown
     * @param summary            text a model wrote about the sources, empty while none was written
     * @param summaryFingerprint fingerprint of the sources the summary was written from
     * @throws NullPointerException if any argument is {@code null}
     */
    public NotebookPayload {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(topicIcon, "topicIcon must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        Objects.requireNonNull(summaryFingerprint, "summaryFingerprint must not be null");
    }

    /**
     * Returns a copy of the payload carrying a different title.
     *
     * @param newTitle name to store instead of the current one
     * @return a payload equal to this one except for its title
     */
    public NotebookPayload withTitle(final String newTitle) {
        return new NotebookPayload(
                newTitle, this.pinned, this.topicIcon, this.summary, this.summaryFingerprint);
    }

    /**
     * Returns a copy of the payload carrying a different pin state.
     *
     * @param newPinned pin state to store instead of the current one
     * @return a payload equal to this one except for its pin state
     */
    public NotebookPayload withPinned(final boolean newPinned) {
        return new NotebookPayload(
                this.title, newPinned, this.topicIcon, this.summary, this.summaryFingerprint);
    }

    /**
     * Returns a copy of the payload carrying a summary and the fingerprint of its material.
     *
     * @param newSummary            text a model wrote about the sources
     * @param newSummaryFingerprint fingerprint of the sources that text was written from
     * @return a payload equal to this one except for its summary and that fingerprint
     */
    public NotebookPayload withSummary(final String newSummary, final String newSummaryFingerprint) {
        return new NotebookPayload(
                this.title, this.pinned, this.topicIcon, newSummary, newSummaryFingerprint);
    }
}
