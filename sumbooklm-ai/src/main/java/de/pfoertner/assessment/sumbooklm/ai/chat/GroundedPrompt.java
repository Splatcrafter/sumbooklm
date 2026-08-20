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

import java.util.List;

/**
 * Builds the instructions an answer is generated under.
 *
 * <h2>Why the Rules Are Explicit</h2>
 * A model asked a question answers it from whatever it knows. The rules below are what turns that
 * into an answer about one notebook: they name the passages as the only permitted material, and they
 * require the refusal to be given rather than a plausible sentence invented in its place. Without
 * them the retrieval would merely be a hint the model is free to ignore.
 *
 * <h2>Citation Format</h2>
 * A citation is written as the Markdown link {@code [n](#source-n)}, where {@code n} is the number of
 * the passage in the block below. The form is Markdown so that a client renders it as part of the
 * text rather than having to parse a convention out of it, and the target is an anchor rather than an
 * address so that the client decides what following a citation does.
 *
 * <h2>No Passages</h2>
 * A question asked about a notebook that retrieved nothing still reaches the model, with a block that
 * says so. The refusal is then phrased in the language of the question and in the context of the
 * conversation, which a sentence assembled here could not be.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class GroundedPrompt {

    /**
     * Rules every answer is generated under.
     */
    private static final String RULES = """
            You are the assistant of a notebook. You answer questions about the sources of that \
            notebook and about nothing else.

            Rules:
            1. Use only the information in the numbered sources below. Never use knowledge from your \
            training and never fill a gap with a guess.
            2. If the sources do not contain what was asked, say plainly that the sources do not \
            answer the question. Do not answer it anyway and do not apologise at length.
            3. Mark every statement you take from a source with a citation directly behind it, \
            written as the Markdown link [n](#source-n), where n is the number of that source.
            4. Cite only numbers that appear below. Never invent a source and never cite one you did \
            not use.
            5. Answer in the language the question was asked in.
            6. Use Markdown for structure. Keep the answer as short as the question allows.""";

    /**
     * Heading the passages are listed under.
     */
    private static final String SOURCES_HEADING = "Sources:";

    /**
     * Prevents instantiation of this prompt builder.
     */
    private GroundedPrompt() {
        throw new AssertionError("GroundedPrompt is a utility class and must not be instantiated");
    }

    /**
     * Builds the instructions for one question.
     *
     * @param passages passages retrieved for the question, in the order they are numbered, never empty
     * @return the text of the system message the model is given
     * @throws IllegalArgumentException if there are no passages, because such a question is answered
     *                                  without a model rather than by telling one that it has nothing
     */
    public static String of(final List<ContextPassage> passages) {
        if (passages.isEmpty()) {
            throw new IllegalArgumentException("A question without passages must not be asked at all");
        }

        final StringBuilder prompt = new StringBuilder(RULES).append("\n\n").append(SOURCES_HEADING);
        for (final ContextPassage passage : passages) {
            prompt.append("\n\n[").append(passage.number()).append("] ").append(passage.displayName())
                    .append('\n').append(passage.text());
        }
        return prompt.toString();
    }
}
