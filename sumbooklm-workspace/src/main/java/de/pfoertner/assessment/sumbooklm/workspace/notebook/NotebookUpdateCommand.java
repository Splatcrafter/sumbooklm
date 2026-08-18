package de.pfoertner.assessment.sumbooklm.workspace.notebook;

import org.jspecify.annotations.Nullable;

/**
 * Fields of a notebook a caller wants to change.
 *
 * <h2>Absent Means Unchanged</h2>
 * Both fields are optional and a {@code null} leaves the stored value alone. That is what allows one
 * command to serve renaming and pinning without the caller having to send back the value it does not
 * intend to touch, which it would otherwise have to read first and could overwrite with a stale one.
 *
 * @param title  name to store, or {@code null} to keep the current one
 * @param pinned pin state to store, or {@code null} to keep the current one
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record NotebookUpdateCommand(@Nullable String title, @Nullable Boolean pinned) {
}
