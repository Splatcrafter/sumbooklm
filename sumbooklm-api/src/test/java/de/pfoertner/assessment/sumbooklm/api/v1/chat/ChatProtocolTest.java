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

package de.pfoertner.assessment.sumbooklm.api.v1.chat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the names a client reads a question and an answer through.
 *
 * <h2>Why the Names Are Fixed</h2>
 * The headers carry the provider, the model and the key of the reader, and the events name the parts
 * of an answer as it arrives. Both are strings on the wire, agreed with a client that is written
 * separately. A rename here compiles, deploys and then simply stops working: a question answered
 * with a model nobody selected, or a stream nothing in the browser listens to.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ChatProtocolTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ChatProtocolTest() {
    }

    /**
     * Verifies the headers a model is selected through.
     */
    @Test
    void theHeadersOfASelectionAreWhereTheyWere() {
        assertThat(ByokHeaders.PROVIDER).isEqualTo("X-AI-Provider");
        assertThat(ByokHeaders.API_KEY).isEqualTo("X-AI-Api-Key");
        assertThat(ByokHeaders.MODEL).isEqualTo("X-AI-Model");
        assertThat(ByokHeaders.BASE_URL).isEqualTo("X-AI-Base-Url");
    }

    /**
     * Verifies that the four headers differ, because one of them carries a key and the others must
     * not be read as it.
     */
    @Test
    void theHeadersDiffer() {
        assertThat(List.of(ByokHeaders.PROVIDER, ByokHeaders.API_KEY, ByokHeaders.MODEL,
                ByokHeaders.BASE_URL)).doesNotHaveDuplicates();
    }

    /**
     * Verifies the names of the events an answer is streamed as.
     */
    @Test
    void theEventsOfAnAnswerAreWhereTheyWere() {
        assertThat(ChatStreamEvent.SOURCES).isEqualTo("sources");
        assertThat(ChatStreamEvent.TOKEN).isEqualTo("token");
        assertThat(ChatStreamEvent.DONE).isEqualTo("done");
        assertThat(ChatStreamEvent.ERROR).isEqualTo("error");
    }

    /**
     * Verifies that the two endings of a stream are told apart, so that a client cannot read a
     * failure as a finished answer.
     */
    @Test
    void theTwoEndingsAreToldApart() {
        assertThat(ChatStreamEvent.DONE).isNotEqualTo(ChatStreamEvent.ERROR);
    }

    /**
     * Verifies that each event carries what its reader needs and nothing else.
     */
    @Test
    void everyEventCarriesItsOwnContent() {
        assertThat(new ChatStreamEvent.Token("Entropy ").text()).isEqualTo("Entropy ");
        assertThat(new ChatStreamEvent.Answer("Entropy never decreases.").answer())
                .isEqualTo("Entropy never decreases.");
        assertThat(new ChatStreamEvent.Failure("the provider refused").reason())
                .isEqualTo("the provider refused");
    }

    /**
     * Verifies that neither holder can be instantiated.
     *
     * @throws NoSuchMethodException if a constructor was removed
     */
    @Test
    void theHoldersCannotBeInstantiated() throws NoSuchMethodException {
        final Constructor<ByokHeaders> headers = ByokHeaders.class.getDeclaredConstructor();
        headers.setAccessible(true);
        final Constructor<ChatStreamEvent> events = ChatStreamEvent.class.getDeclaredConstructor();
        events.setAccessible(true);

        assertThatThrownBy(headers::newInstance).isInstanceOf(InvocationTargetException.class)
                .cause().isInstanceOf(AssertionError.class);
        assertThatThrownBy(events::newInstance).isInstanceOf(InvocationTargetException.class)
                .cause().isInstanceOf(AssertionError.class);
    }
}
