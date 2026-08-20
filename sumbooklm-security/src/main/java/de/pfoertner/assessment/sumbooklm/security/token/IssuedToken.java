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

package de.pfoertner.assessment.sumbooklm.security.token;

import java.time.Instant;
import java.util.UUID;

/**
 * A signed token together with the data the application keeps about it.
 *
 * <h2>Why the Identifier Is Carried</h2>
 * The identifier is the {@code jti} claim of the token. Returning it next to the encoded value spares
 * every caller from decoding the token again just to learn which row it belongs to.
 *
 * @param value     encoded and signed token as it is handed to the client
 * @param id        identifier of the token, matching its {@code jti} claim
 * @param issuedAt  point in time the token was issued
 * @param expiresAt point in time the token stops being accepted
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record IssuedToken(String value, UUID id, Instant issuedAt, Instant expiresAt) {
}
