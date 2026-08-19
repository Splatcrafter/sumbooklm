package de.pfoertner.assessment.sumbooklm.workspace.source;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.embedding.NotebookIndex;
import de.pfoertner.assessment.sumbooklm.domain.workspace.DocumentStatus;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceDocument;
import de.pfoertner.assessment.sumbooklm.domain.workspace.SourceKind;
import de.pfoertner.assessment.sumbooklm.persistence.document.DocumentPayload;
import de.pfoertner.assessment.sumbooklm.persistence.document.SourceDocumentEntity;
import de.pfoertner.assessment.sumbooklm.persistence.document.SourceDocumentMapper;
import de.pfoertner.assessment.sumbooklm.persistence.document.SourceDocumentRepository;
import de.pfoertner.assessment.sumbooklm.persistence.document.SourceReference;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookEntity;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookRepository;
import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import de.pfoertner.assessment.sumbooklm.workspace.notebook.NotebookNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds, lists and removes the sources of a notebook, and records how far indexing has come.
 *
 * <h2>Reached Through the Notebook</h2>
 * Every operation resolves the notebook for the requesting account first. A source is never loaded
 * by its identifier alone, so a caller who knows an identifier of a foreign source still gets the
 * answer that no such source exists.
 *
 * <h2>Storing Comes Before Indexing</h2>
 * Adding a source stores it, marks it as uploaded and answers. The work that makes it searchable is
 * announced as an event and performed once the storing transaction has committed, because a listener
 * that started earlier would look for a row that is not visible yet.
 *
 * <h2>Indexing Again</h2>
 * A source can be sent through the pipeline more than once, and the request looks exactly like the
 * one that follows adding it: the stage goes back to uploaded and the same event is published. That
 * is what lets a failed source be retried and what lets the whole index be rebuilt, and it is also
 * what makes the interface show the source moving again rather than appearing to do nothing.
 *
 * <h2>Stage Transitions</h2>
 * The three methods that move a source between stages are short transactions of their own rather
 * than one transaction spanning the run. Indexing takes seconds, and a transaction held open for
 * that long would occupy a connection while nothing in the database is being touched.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Service
public class SourceDocumentService {

    /**
     * Name used for an upload whose file name is missing or consists of a path only.
     */
    private static final String FALLBACK_DISPLAY_NAME = "Untitled";

    /**
     * Storage of the notebooks, used to resolve and to touch the notebook a source belongs to.
     */
    private final NotebookRepository notebookRepository;

    /**
     * Storage of the sources.
     */
    private final SourceDocumentRepository sourceDocumentRepository;

    /**
     * Translator between source rows, their payload and the domain model.
     */
    private final SourceDocumentMapper sourceDocumentMapper;

    /**
     * Retrieval index the segments of a removed source are taken out of.
     */
    private final NotebookIndex notebookIndex;

    /**
     * Publisher of the event that starts an indexing run.
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
     * @param sourceDocumentMapper     translator between rows, payload and the domain model
     * @param notebookIndex            retrieval index the segments of a source live in
     * @param eventPublisher           publisher of the event that starts an indexing run
     * @param clock                    source of the current time
     */
    public SourceDocumentService(final NotebookRepository notebookRepository,
                                 final SourceDocumentRepository sourceDocumentRepository,
                                 final SourceDocumentMapper sourceDocumentMapper,
                                 final NotebookIndex notebookIndex,
                                 final ApplicationEventPublisher eventPublisher,
                                 final Clock clock) {
        this.notebookRepository = notebookRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.sourceDocumentMapper = sourceDocumentMapper;
        this.notebookIndex = notebookIndex;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Lists the sources of one notebook of an account, oldest first.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to list the sources of
     * @return the sources of the notebook, in the order they were added
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    @Transactional(readOnly = true)
    public List<SourceDocument> list(final UUID userId, final UUID notebookId) {
        requireNotebook(userId, notebookId);
        return this.sourceDocumentRepository
                .findAllByNotebookIdAndUserIdOrderByCreatedAtAsc(notebookId, userId)
                .stream()
                .map(this.sourceDocumentMapper::toDomain)
                .toList();
    }

    /**
     * Reads the names the sources of one notebook are listed under.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to read the names of
     * @return the name of every source of the notebook, by identifier
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> displayNames(final UUID userId, final UUID notebookId) {
        requireNotebook(userId, notebookId);
        final Map<UUID, String> names = new LinkedHashMap<>();
        for (final SourceDocumentEntity entity
                : this.sourceDocumentRepository.findAllByNotebookIdAndUserIdOrderByCreatedAtAsc(notebookId, userId)) {
            names.put(entity.getId(), this.sourceDocumentMapper.readPayload(entity).displayName());
        }
        return names;
    }

    /**
     * Adds an uploaded file to a notebook.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook the file is added to
     * @param fileName   name the file was uploaded under
     * @param content    bytes of the file as they were uploaded
     * @return the stored source, waiting to be indexed
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     * @throws DuplicateSourceException  if the notebook already holds a source with these bytes
     * @throws EmptyUploadException      if the upload carries no bytes
     */
    @Transactional
    public SourceDocument addFile(final UUID userId,
                                  final UUID notebookId,
                                  final String fileName,
                                  final byte[] content) {
        final String name = baseName(fileName);
        if (content.length == 0) {
            throw new EmptyUploadException(name);
        }
        return add(userId, notebookId, content,
                new DocumentPayload(name, SourceKind.FILE, name, DocumentStatus.UPLOADED, 0,
                        SourceFingerprint.ofContent(content)));
    }

    /**
     * Adds a web page to a notebook.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook the page is added to
     * @param address    address of the page
     * @return the stored source, waiting to be indexed
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     * @throws DuplicateSourceException  if the notebook already holds a source with this address
     */
    @Transactional
    public SourceDocument addWebPage(final UUID userId, final UUID notebookId, final String address) {
        final String trimmed = address.strip();
        return add(userId, notebookId, null,
                new DocumentPayload(trimmed, SourceKind.WEB, trimmed, DocumentStatus.UPLOADED, 0,
                        SourceFingerprint.ofAddress(trimmed)));
    }

    /**
     * Removes one source of a notebook, including its segments in the retrieval index.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook the source belongs to
     * @param sourceId   identifier of the source to remove
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     * @throws SourceNotFoundException   if that notebook holds no source with that identifier
     */
    @Transactional
    public void delete(final UUID userId, final UUID notebookId, final UUID sourceId) {
        final NotebookEntity notebook = requireNotebook(userId, notebookId);
        final SourceDocumentEntity entity = requireSource(userId, notebookId, sourceId);
        this.sourceDocumentRepository.delete(entity);
        notebook.setLastActivityAt(now());
        this.notebookIndex.removeSource(sourceId);
    }

    /**
     * Sends a source through the indexing pipeline again.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook the source belongs to
     * @param sourceId   identifier of the source to index again
     * @return the source as it now is, waiting to be indexed
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     * @throws SourceNotFoundException   if that notebook holds no source with that identifier
     */
    @Transactional
    public SourceDocument requestIndexing(final UUID userId, final UUID notebookId, final UUID sourceId) {
        requireNotebook(userId, notebookId);
        final SourceDocumentEntity entity = requireSource(userId, notebookId, sourceId);
        final DocumentPayload payload =
                this.sourceDocumentMapper.readPayload(entity).withStatus(DocumentStatus.UPLOADED);
        store(entity, payload);

        this.eventPublisher.publishEvent(new SourceIndexRequestedEvent(userId, sourceId));
        return this.sourceDocumentMapper.toDomain(entity, payload);
    }

    /**
     * Reads the identity of every source there is, across all accounts.
     *
     * <p>The result is not scoped to an account, unlike everything else this service offers. It
     * serves the rebuild of the retrieval index, which belongs to the process rather than to a user,
     * and which therefore has to see every source there is.
     *
     * @return one entry per stored source, in the order the sources were added
     */
    @Transactional(readOnly = true)
    public List<SourceReference> references() {
        return this.sourceDocumentRepository.findAllReferences();
    }

    /**
     * Marks a source as being indexed and reads what the run needs.
     *
     * @param userId   identifier of the account the source belongs to
     * @param sourceId identifier of the source the run works on
     * @return the values the run works with
     * @throws SourceNotFoundException if the account holds no source with that identifier
     */
    @Transactional
    public IngestionInput beginIndexing(final UUID userId, final UUID sourceId) {
        final SourceDocumentEntity entity = requireSource(userId, sourceId);
        final DocumentPayload payload = this.sourceDocumentMapper.readPayload(entity);
        store(entity, payload.withStatus(DocumentStatus.INDEXING));
        return new IngestionInput(entity.getNotebookId(), payload.kind(), payload.origin(),
                payload.displayName(), entity.getContent(), entity.getExtractedText());
    }

    /**
     * Marks a source as indexed and records what the run produced.
     *
     * @param userId        identifier of the account the source belongs to
     * @param sourceId      identifier of the indexed source
     * @param displayName   name the source is listed under from now on
     * @param tokenCount    number of tokens the indexed text was counted as
     * @param extractedText text the run read out of the source, kept so that a later run can index it
     *                      again without reading the source a second time
     * @throws SourceNotFoundException if the account holds no source with that identifier
     */
    @Transactional
    public void completeIndexing(final UUID userId,
                                 final UUID sourceId,
                                 final String displayName,
                                 final int tokenCount,
                                 final String extractedText) {
        final SourceDocumentEntity entity = requireSource(userId, sourceId);
        final DocumentPayload payload = this.sourceDocumentMapper.readPayload(entity);
        entity.setExtractedText(extractedText);
        store(entity, payload.withIndexingResult(displayName, tokenCount));
    }

    /**
     * Marks a source as one that could not be indexed.
     *
     * @param userId   identifier of the account the source belongs to
     * @param sourceId identifier of the source the run failed on
     * @throws SourceNotFoundException if the account holds no source with that identifier
     */
    @Transactional
    public void failIndexing(final UUID userId, final UUID sourceId) {
        final SourceDocumentEntity entity = requireSource(userId, sourceId);
        store(entity, this.sourceDocumentMapper.readPayload(entity).withStatus(DocumentStatus.ERROR));
    }

    /**
     * Stores a source and announces that it is waiting to be indexed.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook the source is added to
     * @param content    bytes of the uploaded file, or {@code null} for a page
     * @param payload    payload the source starts its life with
     * @return the stored source
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     * @throws DuplicateSourceException  if the notebook already holds the same content
     */
    private SourceDocument add(final UUID userId,
                               final UUID notebookId,
                               final byte[] content,
                               final DocumentPayload payload) {
        final NotebookEntity notebook = requireNotebook(userId, notebookId);
        requireNotAlreadyPresent(userId, notebookId, payload.documentHash());

        final Instant now = now();
        final SourceDocumentEntity entity = new SourceDocumentEntity(
                UUID.randomUUID(),
                userId,
                notebookId,
                now,
                content,
                this.sourceDocumentMapper.writePayload(payload),
                PayloadSchemaVersion.CURRENT);
        final SourceDocumentEntity stored = this.sourceDocumentRepository.save(entity);
        notebook.setLastActivityAt(now);

        this.eventPublisher.publishEvent(new SourceIndexRequestedEvent(userId, stored.getId()));
        return this.sourceDocumentMapper.toDomain(stored, payload);
    }

    /**
     * Writes a payload back onto a row at the current payload schema version.
     *
     * @param entity  row the payload belongs to
     * @param payload payload to store
     */
    private void store(final SourceDocumentEntity entity, final DocumentPayload payload) {
        entity.setPayload(this.sourceDocumentMapper.writePayload(payload));
        entity.setPayloadVersion(PayloadSchemaVersion.CURRENT);
    }

    /**
     * Rejects content a notebook already holds.
     *
     * @param userId       identifier of the account the notebook belongs to
     * @param notebookId   identifier of the notebook to search
     * @param documentHash fingerprint of the content that is about to be added
     * @throws DuplicateSourceException if a source of the notebook carries the same fingerprint
     */
    private void requireNotAlreadyPresent(final UUID userId, final UUID notebookId, final String documentHash) {
        final boolean present = this.sourceDocumentRepository
                .findAllByNotebookIdAndUserIdOrderByCreatedAtAsc(notebookId, userId)
                .stream()
                .map(this.sourceDocumentMapper::readPayload)
                .anyMatch(existing -> existing.documentHash().equals(documentHash));
        if (present) {
            throw new DuplicateSourceException(notebookId);
        }
    }

    /**
     * Loads one notebook of an account.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to load
     * @return the row of the notebook
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    private NotebookEntity requireNotebook(final UUID userId, final UUID notebookId) {
        return this.notebookRepository.findByIdAndUserId(notebookId, userId)
                .orElseThrow(() -> new NotebookNotFoundException(notebookId));
    }

    /**
     * Loads one source of an account.
     *
     * @param userId   identifier of the account the source belongs to
     * @param sourceId identifier of the source to load
     * @return the row of the source
     * @throws SourceNotFoundException if the account holds no source with that identifier
     */
    private SourceDocumentEntity requireSource(final UUID userId, final UUID sourceId) {
        return this.sourceDocumentRepository.findByIdAndUserId(sourceId, userId)
                .orElseThrow(() -> new SourceNotFoundException(sourceId));
    }

    /**
     * Loads one source of a notebook of an account.
     *
     * @param userId     identifier of the account the source belongs to
     * @param notebookId identifier of the notebook the source has to belong to
     * @param sourceId   identifier of the source to load
     * @return the row of the source
     * @throws SourceNotFoundException if the notebook holds no source with that identifier
     */
    private SourceDocumentEntity requireSource(final UUID userId, final UUID notebookId, final UUID sourceId) {
        final SourceDocumentEntity entity = requireSource(userId, sourceId);
        if (!entity.getNotebookId().equals(notebookId)) {
            throw new SourceNotFoundException(sourceId);
        }
        return entity;
    }

    /**
     * Reduces an uploaded file name to the name of the file itself.
     *
     * <p>Some clients submit a whole path rather than a file name. Keeping only the last segment
     * removes the directories of the uploading machine from a value that is displayed, and removes
     * the traversal sequences a path may otherwise carry.
     *
     * @param fileName name the file was uploaded under
     * @return the file name without any leading path, or a fallback when nothing remains
     */
    private static String baseName(final String fileName) {
        final String name = fileName.strip().replace('\\', '/');
        final String last = name.substring(name.lastIndexOf('/') + 1).strip();
        return last.isEmpty() ? FALLBACK_DISPLAY_NAME : last;
    }

    /**
     * Returns the current time at the precision the database keeps.
     *
     * @return the current time truncated to microseconds
     */
    private Instant now() {
        return Instant.now(this.clock).truncatedTo(ChronoUnit.MICROS);
    }
}
