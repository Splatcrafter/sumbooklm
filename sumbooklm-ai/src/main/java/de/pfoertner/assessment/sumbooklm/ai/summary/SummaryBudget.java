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

package de.pfoertner.assessment.sumbooklm.ai.summary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides how much of each source is sent when they are summarised together.
 *
 * <h2>Every Source Gets a Share</h2>
 * A summary that omitted a source would describe a notebook the user does not have, and they cannot
 * see which source was left out. The material is therefore shared out rather than cut off: each
 * source is given an equal part of the budget, whatever is not needed by the short ones is offered to
 * the long ones, and only what still does not fit is dropped from the end of a text.
 *
 * <h2>How the Share Is Computed</h2>
 * The sources are considered from the shortest to the longest. Each is given the smaller of what it
 * needs and what is left divided by how many are still waiting, so a source that needs less than its
 * share hands the rest to those that need more. That is the largest amount every source can be given
 * without any of them being given less than another that is longer.
 *
 * <h2>Characters Rather Than Tokens</h2>
 * The count is in characters, for the reason given in {@code PromptBudget}: the tokenizer belongs to
 * a model that is only known once the request is made, and the budget is set low enough that the
 * approximation being wrong by a factor of two still leaves room.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class SummaryBudget {

    /**
     * Greatest number of characters the sources of one summary may take up together. The instructions
     * and the answer are paid for out of the rest of the same context the chat is budgeted against.
     */
    private static final int MAX_MATERIAL_CHARS = 20_000;

    /**
     * Marker appended to a text that was not sent whole, so that the model can tell a document that
     * ends from one that stops.
     */
    private static final String CUT_MARKER = "\n[...]";

    /**
     * Prevents instantiation of this calculation.
     */
    private SummaryBudget() {
        throw new AssertionError("SummaryBudget is a utility class and must not be instantiated");
    }

    /**
     * Returns the part of each source that is sent, in the order the sources were given in.
     *
     * @param sources sources of the notebook, each with its whole text
     * @return the sources with the text that fits, without those whose share is too small to carry
     *         anything
     */
    public static List<SourceExcerpt> fit(final List<SourceExcerpt> sources) {
        long total = 0;
        for (final SourceExcerpt source : sources) {
            total += source.text().length();
        }
        if (total <= MAX_MATERIAL_CHARS) {
            return List.copyOf(sources);
        }

        final List<Integer> order = new ArrayList<>(sources.size());
        for (int index = 0; index < sources.size(); index += 1) {
            order.add(index);
        }
        order.sort(Comparator.comparingInt(index -> sources.get(index).text().length()));

        final String[] kept = new String[sources.size()];
        int remaining = MAX_MATERIAL_CHARS;
        int waiting = sources.size();
        for (final int index : order) {
            final String text = sources.get(index).text();
            final int share = remaining / waiting;
            waiting -= 1;
            if (text.length() <= share) {
                kept[index] = text;
                remaining -= text.length();
            } else {
                kept[index] = cut(text, share);
                remaining -= share;
            }
        }

        final List<SourceExcerpt> fitted = new ArrayList<>(sources.size());
        for (int index = 0; index < sources.size(); index += 1) {
            if (!kept[index].isEmpty()) {
                fitted.add(new SourceExcerpt(sources.get(index).displayName(), kept[index]));
            }
        }
        return List.copyOf(fitted);
    }

    /**
     * Shortens one text to the room it was given.
     *
     * @param text  text of the source as it is stored
     * @param share number of characters this source may take up
     * @return the beginning of the text, marked as cut, or nothing when the share carries no text
     */
    private static String cut(final String text, final int share) {
        final int room = share - CUT_MARKER.length();
        if (room <= 0) {
            return "";
        }
        return text.substring(0, room).stripTrailing() + CUT_MARKER;
    }
}
