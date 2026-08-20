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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards what each provider is described by.
 *
 * <h2>Why the Values Are Named</h2>
 * The address of a provider is what a request goes to when the client sends none, and whether a key
 * is required decides which requests are refused before anything is contacted. Both are constants,
 * and a change to either is a change to where questions of every user of a deployment travel.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ChatProviderTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ChatProviderTest() {
    }

    /**
     * Verifies the address each provider is reached at when a request names none.
     */
    @Test
    void everyProviderNamesWhereItIsReached() {
        assertThat(ChatProvider.OPENAI.defaultBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(ChatProvider.GROQ.defaultBaseUrl()).isEqualTo("https://api.groq.com/openai/v1");
        assertThat(ChatProvider.OLLAMA.defaultBaseUrl()).isEqualTo("http://localhost:11434");
    }

    /**
     * Verifies that the two hosted providers require a key and the local one does not, which is the
     * distinction every refusal of a selection without a key rests on.
     */
    @Test
    void onlyTheHostedProvidersRequireAKey() {
        assertThat(ChatProvider.OPENAI.isApiKeyRequired()).isTrue();
        assertThat(ChatProvider.GROQ.isApiKeyRequired()).isTrue();
        assertThat(ChatProvider.OLLAMA.isApiKeyRequired()).isFalse();
    }

    /**
     * Verifies that no provider is left without an address, because a selection that fell back to an
     * empty one would be sent nowhere.
     *
     * @param provider provider the case is run for
     */
    @ParameterizedTest
    @EnumSource(ChatProvider.class)
    void noProviderIsLeftWithoutAnAddress(final ChatProvider provider) {
        assertThat(provider.defaultBaseUrl()).isNotBlank().doesNotEndWith("/");
    }
}
