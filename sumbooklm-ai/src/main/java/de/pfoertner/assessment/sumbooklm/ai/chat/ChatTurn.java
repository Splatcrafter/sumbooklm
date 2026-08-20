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

import java.util.Objects;

import de.pfoertner.assessment.sumbooklm.domain.workspace.ChatRole;

/**
 * One earlier message of the conversation, as the model is reminded of it.
 *
 * <h2>Narrower Than a Stored Message</h2>
 * A stored message also carries the point in time it was written at. A model is not told when
 * something was said, only who said it and what, so the timestamp stops at the boundary of this
 * package rather than being passed on and ignored.
 *
 * @param role author of the message
 * @param text content of the message
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ChatTurn(ChatRole role, String text) {

    /**
     * Creates the turn.
     *
     * @param role author of the message
     * @param text content of the message
     * @throws NullPointerException if any argument is {@code null}
     */
    public ChatTurn {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }
}
