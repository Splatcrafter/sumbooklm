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

package de.pfoertner.assessment.sumbooklm.persistence.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatMessage;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;
import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatSession;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadCodec;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadTypes;
import de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the step between a stored conversation and the record every layer above reads.
 *
 * <h2>Two Sources, One Record</h2>
 * A conversation is stored as a row that says when it happened and a payload that says what was
 * said. The mapper is the only place both are read together, so it is also the only place where a
 * transcript could be attached to the wrong row or a moment could be taken from the payload.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ChatSessionMapperTest {

    /**
     * Moment the conversation of the cases was started at.
     */
    private static final Instant STARTED = Instant.parse("2026-08-20T10:15:30Z");

    /**
     * Moment the most recent message of the cases was exchanged at.
     */
    private static final Instant SPOKEN = Instant.parse("2026-08-20T11:00:00Z");

    /**
     * Codec the mapper reads and writes payloads with.
     */
    private PayloadCodec payloadCodec;

    /**
     * Mapper under test.
     */
    private ChatSessionMapper mapper;

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ChatSessionMapperTest() {
    }

    /**
     * Builds the mapper and the codec it reads through.
     */
    @BeforeEach
    void setUp() {
        this.payloadCodec = mock(PayloadCodec.class);
        this.mapper = new ChatSessionMapper(this.payloadCodec);
    }

    /**
     * Verifies that the whole transcript is carried over in the order it was stored in, with every
     * message keeping its author, its text and its moment.
     */
    @Test
    void theWholeTranscriptIsCarriedOver() {
        final ChatSessionEntity entity = entity();
        final ChatSessionPayload payload = new ChatSessionPayload("What is entropy?", List.of(
                new ChatMessagePayload(ChatRole.USER, "What is entropy?", STARTED),
                new ChatMessagePayload(ChatRole.ASSISTANT, "A measure of disorder.", SPOKEN)));

        final ChatSession session = this.mapper.toDomain(entity, payload);

        assertThat(session.messages()).extracting(ChatMessage::role, ChatMessage::text, ChatMessage::createdAt)
                .containsExactly(
                        tuple(ChatRole.USER, "What is entropy?", STARTED),
                        tuple(ChatRole.ASSISTANT, "A measure of disorder.", SPOKEN));
    }

    /**
     * Verifies that the moments of a conversation come from the row and its name from the payload,
     * which is the split the two storage places are divided along.
     */
    @Test
    void theMomentsComeFromTheRowAndTheNameFromThePayload() {
        final ChatSessionEntity entity = entity();

        final ChatSession session =
                this.mapper.toDomain(entity, new ChatSessionPayload("Entropy", List.of()));

        assertThat(session.id()).isEqualTo(entity.getId());
        assertThat(session.notebookId()).isEqualTo(entity.getNotebookId());
        assertThat(session.createdAt()).isEqualTo(STARTED);
        assertThat(session.lastMessageAt()).isEqualTo(SPOKEN);
        assertThat(session.title()).isEqualTo("Entropy");
    }

    /**
     * Verifies that a conversation nobody has spoken in is carried over as an empty transcript
     * rather than being refused.
     */
    @Test
    void aConversationWithoutMessagesIsCarriedOver() {
        assertThat(this.mapper.toDomain(entity(), ChatSessionPayload.empty()).messages()).isEmpty();
    }

    /**
     * Verifies that reading a conversation without a payload of its own decodes the stored bytes
     * under the version the row carries.
     */
    @Test
    void aStoredConversationIsDecodedUnderItsOwnVersion() {
        final ChatSessionEntity entity = entity();
        when(this.payloadCodec.decode(eq(PayloadTypes.CHAT_SESSION), any(), anyInt()))
                .thenReturn(new ChatSessionPayload("Stored", List.of()));

        assertThat(this.mapper.toDomain(entity).title()).isEqualTo("Stored");
        verify(this.payloadCodec).decode(
                PayloadTypes.CHAT_SESSION, entity.getPayload(), entity.getPayloadVersion());
    }

    /**
     * Verifies that a payload is written under the name of its type.
     */
    @Test
    void aPayloadIsWrittenUnderItsType() {
        final ChatSessionPayload payload = ChatSessionPayload.empty();
        when(this.payloadCodec.encode(eq(PayloadTypes.CHAT_SESSION), any())).thenReturn(new byte[]{5});

        assertThat(this.mapper.writePayload(payload)).containsExactly(5);
        verify(this.payloadCodec).encode(PayloadTypes.CHAT_SESSION, payload);
    }

    /**
     * Builds the stored row the cases read from.
     *
     * @return a row of a conversation with a payload nothing in the case decodes
     */
    private static ChatSessionEntity entity() {
        return new ChatSessionEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                STARTED, SPOKEN, new byte[]{1, 2}, PayloadSchemaVersion.CURRENT);
    }
}
