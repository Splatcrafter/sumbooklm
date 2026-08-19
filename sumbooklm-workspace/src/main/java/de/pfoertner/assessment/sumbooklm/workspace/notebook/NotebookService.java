package de.pfoertner.assessment.sumbooklm.workspace.notebook;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.Notebook;
import de.pfoertner.assessment.sumbooklm.domain.workspace.NotebookSummary;
import de.pfoertner.assessment.sumbooklm.persistence.chat.ChatSessionRepository;
import de.pfoertner.assessment.sumbooklm.persistence.document.NotebookSourceCount;
import de.pfoertner.assessment.sumbooklm.persistence.document.SourceDocumentRepository;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookEntity;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookMapper;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookPayload;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookRepository;
import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import de.pfoertner.assessment.sumbooklm.workspace.source.SourceFingerprint;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, lists, changes and removes the notebooks of one account.
 *
 * <h2>Scoping</h2>
 * Every method takes the account the operation is performed for and passes it into the query rather
 * than comparing it against a loaded row. A notebook of another account is therefore not merely
 * rejected, it is never read.
 *
 * <h2>Activity Timestamp</h2>
 * The timestamp orders the overview and is refreshed when the content of a notebook changes, which
 * includes a rename. Pinning does not refresh it: pinning is how a user says where a notebook should
 * appear, and letting it also change the order of the list below would work against that.
 *
 * <h2>The Summary Is Read on Its Own</h2>
 * The text a model wrote about a notebook is stored in its payload but is not part of the notebook
 * every list returns. It is a paragraph that only one screen shows, and reading it with the overview
 * would carry one per entry across the wire for nothing.
 *
 * <h2>Removal</h2>
 * Deleting a notebook deletes the sources and the chat sessions that belong to it in the same
 * transaction. The rows below a notebook carry its identifier rather than a foreign key with a
 * cascade, so the cascade is performed here, where it is visible.
 *
 * The segments of those sources are not removed here. The retrieval index has no transaction of its
 * own, so the deletion is announced and acted on once it has committed; see
 * {@code RetrievalIndexCleanup}.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Service
public class NotebookService {

    /**
     * Storage of the notebooks.
     */
    private final NotebookRepository notebookRepository;

    /**
     * Storage of the sources, used to count and to remove the sources of a notebook.
     */
    private final SourceDocumentRepository sourceDocumentRepository;

    /**
     * Storage of the chat sessions, used to remove the sessions of a notebook.
     */
    private final ChatSessionRepository chatSessionRepository;

    /**
     * Translator between notebook rows, their payload and the domain model.
     */
    private final NotebookMapper notebookMapper;

    /**
     * Publisher of the event announcing a deleted notebook.
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Source of the current time, so that the recorded timestamps are deterministic in tests.
     */
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param notebookRepository       storage of the notebooks
     * @param sourceDocumentRepository storage of the sources
     * @param chatSessionRepository    storage of the chat sessions
     * @param notebookMapper           translator between rows, payload and the domain model
     * @param eventPublisher           publisher of the event announcing a deleted notebook
     * @param clock                    source of the current time
     */
    public NotebookService(final NotebookRepository notebookRepository,
                           final SourceDocumentRepository sourceDocumentRepository,
                           final ChatSessionRepository chatSessionRepository,
                           final NotebookMapper notebookMapper,
                           final ApplicationEventPublisher eventPublisher,
                           final Clock clock) {
        this.notebookRepository = notebookRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.notebookMapper = notebookMapper;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Lists the notebooks of an account, most recently active first.
     *
     * @param userId identifier of the account the notebooks belong to
     * @return the notebooks of the account, each with the number of sources it holds
     */
    @Transactional(readOnly = true)
    public List<Notebook> list(final UUID userId) {
        final List<NotebookEntity> entities =
                this.notebookRepository.findAllByUserIdOrderByLastActivityAtDesc(userId);
        final Map<UUID, Long> sourceCounts = countSources(userId);
        return entities.stream()
                .map(entity -> this.notebookMapper.toDomain(
                        entity, sourceCounts.getOrDefault(entity.getId(), 0L)))
                .toList();
    }

    /**
     * Reads one notebook of an account.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to read
     * @return the notebook, with the number of sources it holds
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    @Transactional(readOnly = true)
    public Notebook get(final UUID userId, final UUID notebookId) {
        final NotebookEntity entity = require(userId, notebookId);
        return this.notebookMapper.toDomain(entity,
                this.sourceDocumentRepository.countByNotebookIdAndUserId(notebookId, userId));
    }

    /**
     * Creates an empty notebook for an account.
     *
     * @param userId identifier of the account the notebook belongs to
     * @param title  name the notebook is created under
     * @return the created notebook
     */
    @Transactional
    public Notebook create(final UUID userId, final String title) {
        final Instant now = now();
        final NotebookPayload payload = new NotebookPayload(title.strip(), false, "", "", "");
        final NotebookEntity entity = new NotebookEntity(
                UUID.randomUUID(),
                userId,
                now,
                now,
                this.notebookMapper.writePayload(payload),
                PayloadSchemaVersion.CURRENT);
        return this.notebookMapper.toDomain(this.notebookRepository.save(entity), payload, 0L);
    }

    /**
     * Changes the title, the pin state or both of one notebook of an account.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to change
     * @param command    fields to change, where an absent field keeps its stored value
     * @return the notebook as it is stored after the change
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    @Transactional
    public Notebook update(final UUID userId, final UUID notebookId, final NotebookUpdateCommand command) {
        final NotebookEntity entity = require(userId, notebookId);
        NotebookPayload payload = this.notebookMapper.readPayload(entity);

        final String title = command.title();
        if (title != null) {
            payload = payload.withTitle(title.strip());
            entity.setLastActivityAt(now());
        }
        final Boolean pinned = command.pinned();
        if (pinned != null) {
            payload = payload.withPinned(pinned);
        }

        entity.setPayload(this.notebookMapper.writePayload(payload));
        entity.setPayloadVersion(PayloadSchemaVersion.CURRENT);
        return this.notebookMapper.toDomain(entity, payload,
                this.sourceDocumentRepository.countByNotebookIdAndUserId(notebookId, userId));
    }

    /**
     * Reads the summary of one notebook of an account.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to read the summary of
     * @return the summary, with an empty text while none has been written
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    @Transactional(readOnly = true)
    public NotebookSummary readSummary(final UUID userId, final UUID notebookId) {
        final NotebookPayload payload = this.notebookMapper.readPayload(require(userId, notebookId));
        final boolean stale = !payload.summary().isEmpty()
                && !payload.summaryFingerprint().equals(fingerprintOfSources(userId, notebookId));
        return new NotebookSummary(notebookId, payload.summary(), stale);
    }

    /**
     * Stores a written summary together with the fingerprint of the sources it was written from.
     *
     * <p>Storing it does not refresh the activity timestamp of the notebook. The order of the
     * overview says when a user last did something with a notebook, and having a summary written is
     * something the application did with the sources they already had.
     *
     * @param userId      identifier of the account the notebook belongs to
     * @param notebookId  identifier of the notebook the summary belongs to
     * @param text        text a model wrote about the sources
     * @param fingerprint fingerprint of the sources that text was written from
     * @return the summary as it is stored, which is stale already if the sources have changed since
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    @Transactional
    public NotebookSummary storeSummary(final UUID userId,
                                        final UUID notebookId,
                                        final String text,
                                        final String fingerprint) {
        final NotebookEntity entity = require(userId, notebookId);
        final NotebookPayload payload = this.notebookMapper.readPayload(entity).withSummary(text, fingerprint);
        entity.setPayload(this.notebookMapper.writePayload(payload));
        entity.setPayloadVersion(PayloadSchemaVersion.CURRENT);
        return new NotebookSummary(
                notebookId, text, !fingerprint.equals(fingerprintOfSources(userId, notebookId)));
    }

    /**
     * Removes one notebook of an account together with everything below it.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to remove
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    @Transactional
    public void delete(final UUID userId, final UUID notebookId) {
        final NotebookEntity entity = require(userId, notebookId);
        this.sourceDocumentRepository.deleteByNotebookIdAndUserId(notebookId, userId);
        this.chatSessionRepository.deleteByNotebookIdAndUserId(notebookId, userId);
        this.notebookRepository.delete(entity);

        this.eventPublisher.publishEvent(new NotebookRemovedEvent(notebookId));
    }

    /**
     * Returns the current time at the precision the database keeps.
     *
     * <p>The value a write returns has to equal the value the next read returns. A timestamp taken at
     * nanosecond precision would not, because the column truncates it, and the response of a creation
     * would then differ from the response of every request that follows it.
     *
     * @return the current time truncated to microseconds
     */
    private Instant now() {
        return Instant.now(this.clock).truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Loads one notebook of an account.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to load
     * @return the row of the notebook
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    private NotebookEntity require(final UUID userId, final UUID notebookId) {
        return this.notebookRepository.findByIdAndUserId(notebookId, userId)
                .orElseThrow(() -> new NotebookNotFoundException(notebookId));
    }

    /**
     * Returns the value the current set of sources of a notebook is recognised by.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to fingerprint the sources of
     * @return the fingerprint of the sources the notebook holds right now
     */
    private String fingerprintOfSources(final UUID userId, final UUID notebookId) {
        return SourceFingerprint.ofSourceSet(
                this.sourceDocumentRepository.findStampsOfNotebook(notebookId, userId));
    }

    /**
     * Counts the sources of every notebook of an account.
     *
     * @param userId identifier of the account the notebooks belong to
     * @return the count per notebook identifier, holding no entry for an empty notebook
     */
    private Map<UUID, Long> countSources(final UUID userId) {
        final Map<UUID, Long> counts = new HashMap<>();
        for (final NotebookSourceCount row : this.sourceDocumentRepository.countPerNotebook(userId)) {
            counts.put(row.getNotebookId(), row.getSourceCount());
        }
        return counts;
    }
}
