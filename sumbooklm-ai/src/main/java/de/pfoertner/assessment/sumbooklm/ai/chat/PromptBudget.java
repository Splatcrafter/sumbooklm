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

package de.pfoertner.assessment.sumbooklm.ai.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decides how much of a conversation still fits into one request.
 *
 * <h2>Why Size Rather Than Count</h2>
 * Ten short exchanges and ten long ones are not the same request. A rule that counts messages says
 * they are, and the long ones are what exceeds the context of a model while the count still reports
 * room.
 *
 * <h2>Characters Rather Than Tokens</h2>
 * Counting tokens needs the tokenizer of the model the request goes to, and this application does not
 * know which model that is until the request is made and never learns its context length. Characters
 * are the approximation that needs neither, and the budget below is set low enough that the
 * approximation being wrong by a factor of two still leaves room.
 *
 * <h2>What Is Never Dropped</h2>
 * The instructions, the retrieved passages and the question stay whole. They are what the answer has
 * to be based on, and an answer built from part of them would be wrong rather than short. Only the
 * conversation is shortened, from the oldest message forwards, because the recent exchanges are what
 * a follow up question refers to.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class PromptBudget {

    /**
     * Greatest number of characters one request is aimed at. At roughly four characters per token for
     * English this is about six thousand tokens, which every model the application can be pointed at
     * accepts, including the small ones that run on a laptop.
     */
    private static final int MAX_PROMPT_CHARS = 24_000;

    /**
     * Characters counted for each message beyond its text, standing for the role and the separators a
     * provider adds around it.
     */
    private static final int MESSAGE_OVERHEAD_CHARS = 16;

    /**
     * Prevents instantiation of this calculation.
     */
    private PromptBudget() {
        throw new AssertionError("PromptBudget is a utility class and must not be instantiated");
    }

    /**
     * Returns the part of a conversation that fits alongside the instructions and the question.
     *
     * @param instructions text of the system message, including the retrieved passages
     * @param question     question that is being asked
     * @param history      earlier messages of the conversation, oldest first
     * @return the most recent messages that fit, oldest first, possibly none of them
     */
    public static List<ChatTurn> fit(final String instructions,
                                     final String question,
                                     final List<ChatTurn> history) {
        int remaining = MAX_PROMPT_CHARS
                - instructions.length() - MESSAGE_OVERHEAD_CHARS
                - question.length() - MESSAGE_OVERHEAD_CHARS;
        if (remaining <= 0) {
            return List.of();
        }

        final List<ChatTurn> kept = new ArrayList<>();
        for (int index = history.size() - 1; index >= 0; index -= 1) {
            final ChatTurn turn = history.get(index);
            final int cost = turn.text().length() + MESSAGE_OVERHEAD_CHARS;
            if (cost > remaining) {
                break;
            }
            remaining -= cost;
            kept.add(turn);
        }
        Collections.reverse(kept);
        return List.copyOf(kept);
    }
}
