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

import java.util.Locale;
import java.util.Objects;

/**
 * The model one request is to be answered by, as the caller selected it.
 *
 * <h2>Validated on Construction</h2>
 * A selection cannot exist in an unusable state: {@link #of(String, String, String, String)} either
 * returns one that can be turned into a client or reports what is missing. That keeps the failure at
 * the edge, where the caller can still be told which of their settings is wrong, rather than inside a
 * stream that has already started.
 *
 * <h2>The Key Is Not Stored</h2>
 * The key lives in this record for the duration of one request and nowhere else. Neither it nor the
 * address it is used with reaches the database or the log, which is what makes a key that is handed
 * in for one question also revoked after it.
 *
 * <h2>The Key Is Not Printed Either</h2>
 * The generated string form of a record contains every component, so a selection that reached a log
 * line or the message of an exception would take the key with it. The form below replaces it with a
 * marker, which is the only place where a rule about a value is worth more than a rule about the
 * places that value must not go.
 *
 * @param provider  service the model is requested from
 * @param modelName name the provider knows the model under
 * @param apiKey    key the provider is addressed with, empty for a provider that needs none
 * @param baseUrl   address the provider is reached at, without a trailing slash
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ModelSelection(ChatProvider provider, String modelName, String apiKey, String baseUrl) {

    /**
     * Creates the selection.
     *
     * @param provider  service the model is requested from
     * @param modelName name the provider knows the model under
     * @param apiKey    key the provider is addressed with
     * @param baseUrl   address the provider is reached at
     * @throws NullPointerException if any argument is {@code null}
     */
    public ModelSelection {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        Objects.requireNonNull(apiKey, "apiKey must not be null");
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
    }

    /**
     * Returns a string form that names the selection without disclosing the key.
     *
     * @return the provider, the model and the address, with the key replaced by a marker
     */
    @Override
    public String toString() {
        return "ModelSelection[provider=" + this.provider
                + ", modelName=" + this.modelName
                + ", apiKey=" + (this.apiKey.isEmpty() ? "<none>" : "<present>")
                + ", baseUrl=" + this.baseUrl + "]";
    }

    /**
     * Builds a selection out of the values a caller presented.
     *
     * @param provider  name of the service, matched against {@link ChatProvider} without regard to case
     * @param modelName name the provider knows the model under
     * @param apiKey    key the provider is addressed with, {@code null} or blank for none
     * @param baseUrl   address the provider is reached at, {@code null} or blank for its default
     * @return the selection, ready to be turned into a client
     * @throws UnusableModelSelectionException if a value is missing, unknown or not usable together
     *                                         with the selected provider
     */
    public static ModelSelection of(final String provider,
                                    final String modelName,
                                    final String apiKey,
                                    final String baseUrl) {
        final ChatProvider selected = parseProvider(provider);
        final String model = value(modelName);
        if (model.isEmpty()) {
            throw new UnusableModelSelectionException("No model was selected");
        }

        final String key = value(apiKey);
        if (selected.isApiKeyRequired() && key.isEmpty()) {
            throw new UnusableModelSelectionException(
                    "Provider " + selected.name() + " requires an API key");
        }

        final String address = value(baseUrl);
        return new ModelSelection(selected, model, key,
                address.isEmpty() ? selected.defaultBaseUrl() : trimTrailingSlash(address));
    }

    /**
     * Resolves the name of a provider into its constant.
     *
     * @param provider name as the caller presented it
     * @return the matching constant
     * @throws UnusableModelSelectionException if the name is missing or belongs to no constant
     */
    private static ChatProvider parseProvider(final String provider) {
        final String name = value(provider);
        if (name.isEmpty()) {
            throw new UnusableModelSelectionException("No provider was selected");
        }
        try {
            return ChatProvider.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            throw new UnusableModelSelectionException("Unknown provider: " + name);
        }
    }

    /**
     * Reduces a presented value to its usable form.
     *
     * @param raw value as the caller presented it, possibly {@code null}
     * @return the value without surrounding whitespace, empty if there was none
     */
    private static String value(final String raw) {
        return raw == null ? "" : raw.strip();
    }

    /**
     * Removes the trailing slashes of an address.
     *
     * @param address address as the caller presented it
     * @return the address without trailing slashes
     */
    private static String trimTrailingSlash(final String address) {
        String result = address;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
