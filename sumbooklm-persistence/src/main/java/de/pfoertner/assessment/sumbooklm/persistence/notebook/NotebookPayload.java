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
 * @param title     name the user gave the notebook
 * @param pinned    whether the user pinned the notebook to the top of their overview
 * @param topicIcon characters standing for the subject of the notebook, empty while unknown
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record NotebookPayload(String title, boolean pinned, String topicIcon) {

    /**
     * Codec that maps the payload onto the format independent tree the migration pipeline operates
     * on. The field names below are part of the persisted format and must only be changed together
     * with a schema version and a data fix that performs the rename.
     */
    public static final Codec<NotebookPayload> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codecs.STRING.fieldOf("title").forGetter(NotebookPayload::title),
                    Codecs.BOOL.fieldOf("pinned").forGetter(NotebookPayload::pinned),
                    Codecs.STRING.fieldOf("topicIcon").forGetter(NotebookPayload::topicIcon)
            ).apply(instance, NotebookPayload::new));

    /**
     * Creates the payload.
     *
     * @param title     name the user gave the notebook
     * @param pinned    whether the user pinned the notebook to the top of their overview
     * @param topicIcon characters standing for the subject of the notebook, empty while unknown
     * @throws NullPointerException if {@code title} or {@code topicIcon} is {@code null}
     */
    public NotebookPayload {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(topicIcon, "topicIcon must not be null");
    }

    /**
     * Returns a copy of the payload carrying a different title.
     *
     * @param newTitle name to store instead of the current one
     * @return a payload equal to this one except for its title
     */
    public NotebookPayload withTitle(final String newTitle) {
        return new NotebookPayload(newTitle, this.pinned, this.topicIcon);
    }

    /**
     * Returns a copy of the payload carrying a different pin state.
     *
     * @param newPinned pin state to store instead of the current one
     * @return a payload equal to this one except for its pin state
     */
    public NotebookPayload withPinned(final boolean newPinned) {
        return new NotebookPayload(this.title, newPinned, this.topicIcon);
    }
}
