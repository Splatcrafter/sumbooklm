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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the step that turns four request headers into a usable model.
 *
 * <h2>Why It Is Tested Alone</h2>
 * The headers arrive from a browser and are the one part of a question the server does not control.
 * Everything the rule refuses is therefore a request somebody can send, and each refusal has to name
 * what was wrong rather than fail somewhere inside a provider client. Driving these cases through
 * the API would answer them with the same status and hide which of them was met.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ModelSelectionTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ModelSelectionTest() {
    }

    /**
     * Verifies that a complete selection keeps its values and that the name of the provider is read
     * without regard to how it was written.
     */
    @Test
    void aCompleteSelectionIsAccepted() {
        final ModelSelection selection =
                ModelSelection.of("OpenAi", " gpt-4o-mini ", " sk-secret ", "https://proxy.test/v1");

        assertThat(selection.provider()).isEqualTo(ChatProvider.OPENAI);
        assertThat(selection.modelName()).isEqualTo("gpt-4o-mini");
        assertThat(selection.apiKey()).isEqualTo("sk-secret");
        assertThat(selection.baseUrl()).isEqualTo("https://proxy.test/v1");
    }

    /**
     * Verifies that a selection without an address falls back to the address of its provider, which
     * is what makes the header optional for the providers this application knows.
     */
    @Test
    void anAbsentAddressFallsBackToTheProvider() {
        assertThat(ModelSelection.of("openai", "gpt-4o-mini", "sk-secret", null).baseUrl())
                .isEqualTo(ChatProvider.OPENAI.defaultBaseUrl());
        assertThat(ModelSelection.of("ollama", "llama3", "", "   ").baseUrl())
                .isEqualTo(ChatProvider.OLLAMA.defaultBaseUrl());
    }

    /**
     * Verifies that trailing slashes are removed from an address, however many of them were sent,
     * because a client appends its own path and would otherwise request a doubled separator.
     *
     * @param address address the case is run for
     */
    @ParameterizedTest
    @ValueSource(strings = {"https://proxy.test/v1/", "https://proxy.test/v1//", "https://proxy.test/v1///"})
    void trailingSlashesAreRemovedFromAnAddress(final String address) {
        assertThat(ModelSelection.of("groq", "llama3", "gsk-secret", address).baseUrl())
                .isEqualTo("https://proxy.test/v1");
    }

    /**
     * Verifies that an address made of slashes alone is reduced to nothing rather than kept, which
     * is the edge of the rule above and would otherwise produce an address of one character.
     */
    @Test
    void anAddressOfSlashesAloneIsReducedToNothing() {
        assertThat(ModelSelection.of("groq", "llama3", "gsk-secret", "///").baseUrl()).isEmpty();
    }

    /**
     * Verifies that a question without a provider is refused, which is what a request carrying no
     * header at all amounts to.
     *
     * @param provider provider the case is run for
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void aSelectionWithoutAProviderIsRefused(final String provider) {
        assertThatThrownBy(() -> ModelSelection.of(provider, "gpt-4o-mini", "sk-secret", null))
                .isInstanceOf(UnusableModelSelectionException.class)
                .hasMessageContaining("No provider was selected");
    }

    /**
     * Verifies that a provider this application does not know is refused by name, so that a
     * misspelled header is answered with what was misspelled.
     */
    @Test
    void anUnknownProviderIsRefusedByName() {
        assertThatThrownBy(() -> ModelSelection.of("anthropic", "claude", "key", null))
                .isInstanceOf(UnusableModelSelectionException.class)
                .hasMessageContaining("anthropic");
    }

    /**
     * Verifies that a selection without a model is refused, because the provider alone does not say
     * what is to answer.
     *
     * @param modelName model the case is run for
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void aSelectionWithoutAModelIsRefused(final String modelName) {
        assertThatThrownBy(() -> ModelSelection.of("openai", modelName, "sk-secret", null))
                .isInstanceOf(UnusableModelSelectionException.class)
                .hasMessageContaining("No model was selected");
    }

    /**
     * Verifies that a provider which bills for its answers is refused without a key, and that the
     * refusal names the provider rather than the key.
     *
     * @param apiKey key the case is run for
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aPaidProviderWithoutAKeyIsRefused(final String apiKey) {
        assertThatThrownBy(() -> ModelSelection.of("openai", "gpt-4o-mini", apiKey, null))
                .isInstanceOf(UnusableModelSelectionException.class)
                .hasMessageContaining("OPENAI");
        assertThatThrownBy(() -> ModelSelection.of("groq", "llama3", apiKey, null))
                .isInstanceOf(UnusableModelSelectionException.class)
                .hasMessageContaining("GROQ");
    }

    /**
     * Verifies that a provider running next to the application needs no key, which is the case the
     * distinction on the provider exists for.
     */
    @Test
    void aLocalProviderNeedsNoKey() {
        final ModelSelection selection = ModelSelection.of("ollama", "llama3", null, null);

        assertThat(selection.apiKey()).isEmpty();
        assertThat(selection.provider()).isEqualTo(ChatProvider.OLLAMA);
    }

    /**
     * Verifies that the key never appears in the text of a selection, because that text is what a
     * log statement or a failure message would carry it into.
     */
    @Test
    void theKeyIsNotWrittenOut() {
        final String text = ModelSelection.of("openai", "gpt-4o-mini", "sk-super-secret", null).toString();

        assertThat(text).doesNotContain("sk-super-secret").contains("<present>", "gpt-4o-mini", "OPENAI");
        assertThat(ModelSelection.of("ollama", "llama3", "", null).toString()).contains("<none>");
    }

    /**
     * Verifies that the record itself still refuses missing parts, so that a caller building one
     * directly cannot skip what the factory method checks.
     */
    @Test
    void theRecordRefusesMissingParts() {
        assertThatThrownBy(() -> new ModelSelection(null, "m", "k", "u"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("provider");
        assertThatThrownBy(() -> new ModelSelection(ChatProvider.OPENAI, null, "k", "u"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("modelName");
        assertThatThrownBy(() -> new ModelSelection(ChatProvider.OPENAI, "m", null, "u"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("apiKey");
        assertThatThrownBy(() -> new ModelSelection(ChatProvider.OPENAI, "m", "k", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("baseUrl");
    }
}
