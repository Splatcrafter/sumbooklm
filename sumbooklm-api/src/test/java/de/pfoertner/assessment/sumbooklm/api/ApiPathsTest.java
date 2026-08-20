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

package de.pfoertner.assessment.sumbooklm.api;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the addresses the API is served under.
 *
 * <h2>Why They Are Named Here</h2>
 * Every path is built from the one above it, so a change near the root moves everything below it at
 * once. The clients of this application are generated from the description these constants produce,
 * which means a moved path is a client that no longer reaches the server, and nothing about that
 * fails to compile. The transport rule reads the same root as a prefix, so a path that stopped
 * beginning with it would also stop being protected.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ApiPathsTest {

    /**
     * Creates the test class. The instance is created by JUnit and holds no state.
     */
    ApiPathsTest() {
    }

    /**
     * Verifies the root every path of this version is built from.
     */
    @Test
    void theRootIsWhereItWas() {
        assertThat(ApiPaths.BASE).isEqualTo("/api");
        assertThat(ApiPaths.V1).isEqualTo("/api/v1");
    }

    /**
     * Verifies the paths an account is created and used through.
     */
    @Test
    void theAuthenticationPathsAreWhereTheyWere() {
        assertThat(ApiPaths.V1_REGISTER).isEqualTo("/api/v1/register");
        assertThat(ApiPaths.V1_LOGIN).isEqualTo("/api/v1/login");
        assertThat(ApiPaths.V1_TOKEN_REFRESH).isEqualTo("/api/v1/token/refresh");
        assertThat(ApiPaths.V1_LOGOUT).isEqualTo("/api/v1/logout");
    }

    /**
     * Verifies that everything belonging to a notebook is served below that notebook, so that the
     * account owning it is checked once for all of them.
     */
    @Test
    void everythingOfANotebookIsServedBelowIt() {
        assertThat(ApiPaths.V1_NOTEBOOK).isEqualTo("/api/v1/notebooks/{notebookId}");
        assertThat(ApiPaths.V1_NOTEBOOK_SOURCES).startsWith(ApiPaths.V1_NOTEBOOK);
        assertThat(ApiPaths.V1_NOTEBOOK_SOURCE_FILES).startsWith(ApiPaths.V1_NOTEBOOK_SOURCES);
        assertThat(ApiPaths.V1_NOTEBOOK_SOURCE_LINKS).startsWith(ApiPaths.V1_NOTEBOOK_SOURCES);
        assertThat(ApiPaths.V1_NOTEBOOK_SOURCE_REFRESH).startsWith(ApiPaths.V1_NOTEBOOK_SOURCE);
        assertThat(ApiPaths.V1_NOTEBOOK_SUMMARY).startsWith(ApiPaths.V1_NOTEBOOK);
        assertThat(ApiPaths.V1_NOTEBOOK_CHAT_QUESTIONS).startsWith(ApiPaths.V1_NOTEBOOK_CHAT);
        assertThat(ApiPaths.V1_NOTEBOOK_CHAT_STOP).startsWith(ApiPaths.V1_NOTEBOOK_CHAT);
    }

    /**
     * Verifies that the paths naming a source and a conversation each carry their own placeholder,
     * so that neither is reached without being named.
     */
    @Test
    void thePathsThatNameSomethingCarryItsPlaceholder() {
        assertThat(ApiPaths.V1_NOTEBOOK_SOURCE).endsWith("/{sourceId}");
        assertThat(ApiPaths.V1_NOTEBOOK_CHAT).endsWith("/{sessionId}");
        assertThat(ApiPaths.V1_NOTEBOOK_CHAT_QUESTIONS).endsWith("/questions");
    }

    /**
     * Verifies that every path of the API begins with the root the transport rule protects, so that
     * none of them can be reached without transport security where a deployment demands it.
     *
     * @throws IllegalAccessException if a constant of the holder cannot be read
     */
    @Test
    void everyPathIsProtectedByTheTransportRule() throws IllegalAccessException {
        final List<String> paths = new ArrayList<>();
        for (final Field field : ApiPaths.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                paths.add((String) field.get(null));
            }
        }

        assertThat(paths).isNotEmpty().allSatisfy(path -> assertThat(path).startsWith(ApiPaths.BASE));
    }

    /**
     * Verifies that the holder cannot be instantiated.
     *
     * @throws NoSuchMethodException if the constructor was removed
     */
    @Test
    void theHolderCannotBeInstantiated() throws NoSuchMethodException {
        final Constructor<ApiPaths> constructor = ApiPaths.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(AssertionError.class);
    }
}
