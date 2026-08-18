package de.pfoertner.assessment.sumbooklm.domain.workspace;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A workspace of one user, collecting the sources an answer may be grounded in.
 *
 * <h2>Ownership</h2>
 * A notebook belongs to exactly one account and is never shared. {@code ownerId} is therefore not
 * merely a reference but the filter every read and every write has to carry, so that the identifier
 * of a notebook alone is not enough to reach it.
 *
 * <h2>Topic Icon</h2>
 * The icon is a short string of user-perceived characters that stands for the subject of the
 * notebook. It is derived from the content of the notebook once sources exist and is empty until
 * then, which the presentation layer answers with an icon of its own rather than with a placeholder
 * character.
 *
 * <h2>Source Count</h2>
 * The count is derived from the sources that currently belong to the notebook rather than stored
 * with it, so it cannot drift away from the number of rows it describes.
 *
 * @param id             stable identifier of the notebook, never {@code null}
 * @param ownerId        identifier of the account the notebook belongs to, never {@code null}
 * @param title          name the user gave the notebook, never {@code null}
 * @param pinned         whether the user pinned the notebook to the top of their overview
 * @param topicIcon      characters standing for the subject of the notebook, empty while unknown,
 *                       never {@code null}
 * @param createdAt      point in time the notebook was created, never {@code null}
 * @param lastActivityAt point in time the notebook was last opened or changed, never {@code null}
 * @param sourceCount    number of sources currently belonging to the notebook
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record Notebook(UUID id,
                       UUID ownerId,
                       String title,
                       boolean pinned,
                       String topicIcon,
                       Instant createdAt,
                       Instant lastActivityAt,
                       long sourceCount) {

    /**
     * Creates the notebook.
     *
     * @param id             stable identifier of the notebook
     * @param ownerId        identifier of the account the notebook belongs to
     * @param title          name the user gave the notebook
     * @param pinned         whether the user pinned the notebook to the top of their overview
     * @param topicIcon      characters standing for the subject of the notebook, empty while unknown
     * @param createdAt      point in time the notebook was created
     * @param lastActivityAt point in time the notebook was last opened or changed
     * @param sourceCount    number of sources currently belonging to the notebook
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code sourceCount} is negative
     */
    public Notebook {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(topicIcon, "topicIcon must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(lastActivityAt, "lastActivityAt must not be null");
        if (sourceCount < 0) {
            throw new IllegalArgumentException("sourceCount must not be negative");
        }
    }
}
