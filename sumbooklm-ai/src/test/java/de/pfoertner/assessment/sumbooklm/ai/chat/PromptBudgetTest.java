package de.pfoertner.assessment.sumbooklm.ai.chat;

import java.util.ArrayList;
import java.util.List;

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the rule that decides how much of a conversation goes into one request.
 *
 * <h2>Why It Is Tested Alone</h2>
 * The rule is arithmetic over lengths, and what makes it worth stating is the cases at its edges: a
 * conversation that fits entirely, one that fits partly, and one whose most recent message alone does
 * not. Driving those through a model would mean choosing texts by their size and asserting on what a
 * provider received.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class PromptBudgetTest {

    /**
     * Instructions of a request that leaves plenty of room, standing for a notebook with few passages.
     */
    private static final String SMALL_INSTRUCTIONS = "Answer from the sources.";

    /**
     * Question every case is asked with.
     */
    private static final String QUESTION = "And the second law?";

    /**
     * Creates the test class.
     */
    PromptBudgetTest() {
    }

    /**
     * Verifies that a short conversation is kept whole and in the order it was held in.
     */
    @Test
    void aShortConversationIsKeptWhole() {
        final List<ChatTurn> history = List.of(
                new ChatTurn(ChatRole.USER, "What is entropy?"),
                new ChatTurn(ChatRole.ASSISTANT, "A measure of disorder."));

        assertThat(PromptBudget.fit(SMALL_INSTRUCTIONS, QUESTION, history)).isEqualTo(history);
    }

    /**
     * Verifies that a conversation which no longer fits keeps its most recent messages and loses the
     * oldest, because a follow up question refers to what was said last.
     */
    @Test
    void aLongConversationKeepsItsMostRecentMessages() {
        final List<ChatTurn> history = new ArrayList<>();
        for (int message = 0; message < 20; message += 1) {
            history.add(new ChatTurn(ChatRole.ASSISTANT, message + " " + "x".repeat(2_000)));
        }

        final List<ChatTurn> kept = PromptBudget.fit(SMALL_INSTRUCTIONS, QUESTION, history);

        assertThat(kept).isNotEmpty().hasSizeLessThan(history.size());
        assertThat(kept.getLast()).isEqualTo(history.getLast());
        assertThat(history.subList(history.size() - kept.size(), history.size())).isEqualTo(kept);
    }

    /**
     * Verifies that a single message beyond the whole budget is left out rather than sent, and that
     * the messages before it are left out with it, because a conversation is only sent as a run of
     * its most recent messages.
     */
    @Test
    void aMessageBeyondTheBudgetIsLeftOut() {
        final List<ChatTurn> history = List.of(
                new ChatTurn(ChatRole.USER, "What is entropy?"),
                new ChatTurn(ChatRole.ASSISTANT, "y".repeat(100_000)));

        assertThat(PromptBudget.fit(SMALL_INSTRUCTIONS, QUESTION, history)).isEmpty();
    }

    /**
     * Verifies that instructions which fill the budget on their own leave no conversation at all,
     * which is what happens when a notebook returns as many passages as a request may hold.
     */
    @Test
    void instructionsThatFillTheBudgetLeaveNoConversation() {
        final List<ChatTurn> history = List.of(new ChatTurn(ChatRole.USER, "What is entropy?"));

        assertThat(PromptBudget.fit("z".repeat(100_000), QUESTION, history)).isEmpty();
    }

    /**
     * Verifies that a conversation nobody has held yet is handled like any other, so that the first
     * question of a conversation needs no case of its own.
     */
    @Test
    void anEmptyConversationStaysEmpty() {
        assertThat(PromptBudget.fit(SMALL_INSTRUCTIONS, QUESTION, List.of())).isEmpty();
    }
}
