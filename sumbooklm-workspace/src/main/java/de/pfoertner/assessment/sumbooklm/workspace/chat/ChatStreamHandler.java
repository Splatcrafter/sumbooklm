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

package de.pfoertner.assessment.sumbooklm.workspace.chat;

import java.util.List;

import de.pfoertner.assessment.sumbooklm.ai.chat.AnswerStreamHandler;

/**
 * Receiver of one answer, including the sources it was allowed to draw on.
 *
 * <h2>Order of the Callbacks</h2>
 * The sources are reported once, before the first part of the answer. A client may therefore assume
 * that every citation it renders refers to a source it has already been told about, and does not have
 * to hold text back until the end.
 *
 * <h2>Relation to the Engine</h2>
 * The interface adds the notebook side of a turn to what the engine already reports. The same object
 * is handed down to the engine, so an answer arrives through one receiver rather than being stitched
 * together from two.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface ChatStreamHandler extends AnswerStreamHandler {

    /**
     * Reports the sources the answer may cite.
     *
     * @param sources sources the retrieved passages came from, in the order they are numbered
     */
    void onSources(List<RetrievedSource> sources);
}
