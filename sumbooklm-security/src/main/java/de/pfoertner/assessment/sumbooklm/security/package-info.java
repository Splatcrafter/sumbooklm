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

/**
 * Authentication and token lifecycle of the application.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Register accounts and verify credentials.</li>
 *   <li>Issue, rotate and revoke the access and refresh token pair.</li>
 *   <li>Derive the parameters a client encrypts its stored token pair with.</li>
 *   <li>Provide the marker that makes an operation verify its session against the database.</li>
 * </ul>
 *
 * <h2>Boundary</h2>
 * The module knows nothing about HTTP. It receives the data a request carries, such as the network
 * address of the caller, as ordinary arguments, and it returns objects rather than responses. Which
 * routes are reachable without authentication, and how a token reaches a response, is decided in the
 * transport layer.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.security;
