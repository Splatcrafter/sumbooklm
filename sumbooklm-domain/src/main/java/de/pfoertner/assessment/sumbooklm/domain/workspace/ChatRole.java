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

package de.pfoertner.assessment.sumbooklm.domain.workspace;

/**
 * Author of one message of a conversation.
 *
 * <h2>Two Authors Only</h2>
 * A conversation carries what the user asked and what the notebook answered. The instructions the
 * model is given are not part of it: they are rebuilt from the sources on every turn, so storing them
 * would freeze a prompt that is meant to change with the pipeline.
 *
 * <h2>Persistence</h2>
 * The constants are persisted by name rather than by ordinal, so that the order of the declarations
 * below carries no meaning for stored data.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public enum ChatRole {

    /**
     * The message was written by the account holding the conversation.
     */
    USER,

    /**
     * The message was generated from the sources of the notebook.
     */
    ASSISTANT
}
