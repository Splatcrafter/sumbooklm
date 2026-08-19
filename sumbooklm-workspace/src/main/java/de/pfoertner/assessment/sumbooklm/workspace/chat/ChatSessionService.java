package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.ai.chat.ChatTurn;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatSession;
import de.pfoertner.assessment.sumbooklm.persistence.chat.ChatMessagePayload;
import de.pfoertner.assessment.sumbooklm.persistence.chat.ChatSessionEntity;
import de.pfoertner.assessment.sumbooklm.persistence.chat.ChatSessionMapper;
import de.pfoertner.assessment.sumbooklm.persistence.chat.ChatSessionPayload;
import de.pfoertner.assessment.sumbooklm.persistence.chat.ChatSessionRepository;
import de.pfoertner.assessment.sumbooklm.persistence.notebook.NotebookRepository;
import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import de.pfoertner.assessment.sumbooklm.workspace.notebook.NotebookNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the conversations held inside a notebook.
 *
 * <h2>Reached Through the Notebook</h2>
 * As with every aggregate below a notebook, a conversation is resolved for the requesting account
 * first, and a conversation that belongs to another notebook of the same account is treated as
 * missing. A caller who knows an identifier is therefore not able to reach anything with it.
 *
 * <h2>Several Conversations</h2>
 * A notebook holds as many conversations as its user starts. Each is its own row with its own
 * transcript, so a second question about a different subject does not have to carry the first one as
 * context, and neither has to be deleted to get away from it.
 *
 * <h2>Nothing Is Created by Reading</h2>
 * Opening a notebook lists what is there and creates nothing. A conversation exists because somebody
 * asked for it, which is what keeps a notebook that is only being read free of writes.
 *
 * <h2>Two Questions at Once</h2>
 * Opening a turn appends to a payload it has just read, and a user with two Sumbooks open can do that
 * twice at the same moment. The notebook is therefore touched first, which takes a write lock on its
 * row and makes the two turns follow one another instead of both writing a transcript that is missing
 * the other's question.
 *
 * <h2>How Much History the Model Sees</h2>
 * A turn is opened with the most recent messages rather than with all of them. The whole transcript
 * would grow the request of every further question without bound, and it is the user who pays for
 * that, because the key the request is made with is theirs.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Service
public class ChatSessionService {

    /**
     * Number of stored messages a new question is asked with. Two messages make one exchange, so the
     * value is the last five questions and their answers.
     */
    private static final int HISTORY_LIMIT = 10;

    /**
     * Greatest number of characters a derived title is allowed to have.
     */
    private static final int TITLE_LIMIT = 80;

    /**
     * Marker appended to a title that had to be shortened.
     */
    private static final String TITLE_ELLIPSIS = "...";

    /**
     * Storage of the notebooks, used to resolve and to touch the notebook a conversation belongs to.
     */
    private final NotebookRepository notebookRepository;

    /**
     * Storage of the conversations.
     */
    private final ChatSessionRepository chatSessionRepository;

    /**
     * Translator between session rows, their payload and the domain model.
     */
    private final ChatSessionMapper chatSessionMapper;

    /**
     * Source of the current time, so that the recorded timestamps are deterministic in tests.
     */
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param notebookRepository    storage of the notebooks
     * @param chatSessionRepository storage of the conversations
     * @param chatSessionMapper     translator between rows, payload and the domain model
     * @param clock                 source of the current time
     */
    public ChatSessionService(final NotebookRepository notebookRepository,
                              final ChatSessionRepository chatSessionRepository,
                              final ChatSessionMapper chatSessionMapper,
                              final Clock clock) {
        this.notebookRepository = notebookRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatSessionMapper = chatSessionMapper;
        this.clock = clock;
    }

    /**
     * Lists the conversations of one notebook of an account, most recently used first.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to list the conversations of
     * @return the conversations of the notebook, each with its whole transcript
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    @Transactional(readOnly = true)
    public List<ChatSession> conversations(final UUID userId, final UUID notebookId) {
        requireNotebook(userId, notebookId);
        return this.chatSessionRepository
                .findAllByNotebookIdAndUserIdOrderByLastMessageAtDesc(notebookId, userId)
                .stream()
                .map(this.chatSessionMapper::toDomain)
                .toList();
    }

    /**
     * Reads one conversation of one notebook of an account.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook the conversation belongs to
     * @param sessionId  identifier of the conversation to read
     * @return the conversation with its whole transcript
     * @throws NotebookNotFoundException    if the account holds no notebook with that identifier
     * @throws ChatSessionNotFoundException if that notebook holds no such conversation
     */
    @Transactional(readOnly = true)
    public ChatSession conversation(final UUID userId, final UUID notebookId, final UUID sessionId) {
        requireNotebook(userId, notebookId);
        return this.chatSessionMapper.toDomain(requireSession(userId, notebookId, sessionId));
    }

    /**
     * Starts a conversation in one notebook of an account.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook the conversation belongs to
     * @return the new conversation, without a title and without messages
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    @Transactional
    public ChatSession create(final UUID userId, final UUID notebookId) {
        final Instant now = now();
        touchNotebook(userId, notebookId, now);

        final ChatSessionEntity entity = this.chatSessionRepository.save(new ChatSessionEntity(
                UUID.randomUUID(),
                userId,
                notebookId,
                now,
                now,
                this.chatSessionMapper.writePayload(ChatSessionPayload.empty()),
                PayloadSchemaVersion.CURRENT));
        return this.chatSessionMapper.toDomain(entity);
    }

    /**
     * Removes one conversation of one notebook of an account.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook the conversation belongs to
     * @param sessionId  identifier of the conversation to remove
     * @throws NotebookNotFoundException    if the account holds no notebook with that identifier
     * @throws ChatSessionNotFoundException if that notebook holds no such conversation
     */
    @Transactional
    public void delete(final UUID userId, final UUID notebookId, final UUID sessionId) {
        touchNotebook(userId, notebookId, now());
        this.chatSessionRepository.delete(requireSession(userId, notebookId, sessionId));
    }

    /**
     * Stores a question and reads what generating its answer needs.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook the question was asked in
     * @param sessionId  identifier of the conversation the question continues
     * @param question   question that was asked
     * @return the conversation as it was before the question, together with its identifier
     * @throws NotebookNotFoundException    if the account holds no notebook with that identifier
     * @throws ChatSessionNotFoundException if that notebook holds no such conversation
     */
    @Transactional
    public ChatTurnContext beginTurn(final UUID userId,
                                     final UUID notebookId,
                                     final UUID sessionId,
                                     final String question) {
        final Instant now = now();
        touchNotebook(userId, notebookId, now);

        final ChatSessionEntity entity = this.chatSessionRepository
                .findForUpdateByIdAndUserId(sessionId, userId)
                .filter(session -> session.getNotebookId().equals(notebookId))
                .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));

        final ChatSessionPayload payload = this.chatSessionMapper.readPayload(entity);
        final List<ChatTurn> history = recentTurns(payload);

        ChatSessionPayload updated =
                payload.withMessage(new ChatMessagePayload(ChatRole.USER, question, now));
        if (updated.title().isBlank()) {
            updated = updated.withTitle(titleOf(question));
        }
        store(entity, updated);
        entity.setLastMessageAt(now);

        return new ChatTurnContext(entity.getId(), notebookId, question, history);
    }

    /**
     * Appends a generated answer to a conversation.
     *
     * @param userId    identifier of the account the conversation belongs to
     * @param sessionId identifier of the conversation the answer belongs to
     * @param answer    answer as the model produced it
     * @throws ChatSessionNotFoundException if the account holds no conversation with that identifier
     */
    @Transactional
    public void recordAnswer(final UUID userId, final UUID sessionId, final String answer) {
        final ChatSessionEntity entity = this.chatSessionRepository
                .findForUpdateByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));
        final Instant now = now();
        store(entity, this.chatSessionMapper.readPayload(entity)
                .withMessage(new ChatMessagePayload(ChatRole.ASSISTANT, answer, now)));
        entity.setLastMessageAt(now);
    }

    /**
     * Reads the most recent messages of a conversation in the form the model is reminded of them in.
     *
     * @param payload payload of the conversation
     * @return the last messages of the conversation, oldest first
     */
    private static List<ChatTurn> recentTurns(final ChatSessionPayload payload) {
        final List<ChatMessagePayload> messages = payload.messages();
        final int from = Math.max(0, messages.size() - HISTORY_LIMIT);
        return messages.subList(from, messages.size()).stream()
                .map(message -> new ChatTurn(message.role(), message.text()))
                .toList();
    }

    /**
     * Derives the name a conversation is listed under from its first question.
     *
     * @param question first question of the conversation
     * @return the question, shortened to the length a title may have
     */
    private static String titleOf(final String question) {
        final String stripped = question.strip();
        if (stripped.length() <= TITLE_LIMIT) {
            return stripped;
        }
        return stripped.substring(0, TITLE_LIMIT - TITLE_ELLIPSIS.length()).strip() + TITLE_ELLIPSIS;
    }

    /**
     * Writes a payload back onto a row at the current payload schema version.
     *
     * @param entity  row the payload belongs to
     * @param payload payload to store
     */
    private void store(final ChatSessionEntity entity, final ChatSessionPayload payload) {
        entity.setPayload(this.chatSessionMapper.writePayload(payload));
        entity.setPayloadVersion(PayloadSchemaVersion.CURRENT);
    }

    /**
     * Loads one conversation of one notebook of an account.
     *
     * @param userId     identifier of the account the conversation belongs to
     * @param notebookId identifier of the notebook the conversation has to belong to
     * @param sessionId  identifier of the conversation to load
     * @return the row of the conversation
     * @throws ChatSessionNotFoundException if that notebook holds no such conversation
     */
    private ChatSessionEntity requireSession(final UUID userId, final UUID notebookId, final UUID sessionId) {
        return this.chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .filter(session -> session.getNotebookId().equals(notebookId))
                .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));
    }

    /**
     * Reads one notebook of an account, only to establish that it exists.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to check
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    private void requireNotebook(final UUID userId, final UUID notebookId) {
        this.notebookRepository.findByIdAndUserId(notebookId, userId)
                .orElseThrow(() -> new NotebookNotFoundException(notebookId));
    }

    /**
     * Refreshes the activity timestamp of one notebook of an account and locks its row.
     *
     * @param userId     identifier of the account the notebook belongs to
     * @param notebookId identifier of the notebook to touch
     * @param at         point in time to record as the most recent activity
     * @throws NotebookNotFoundException if the account holds no notebook with that identifier
     */
    private void touchNotebook(final UUID userId, final UUID notebookId, final Instant at) {
        if (this.notebookRepository.touch(notebookId, userId, at) == 0) {
            throw new NotebookNotFoundException(notebookId);
        }
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
