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
 * Management of the workspaces a user organises their sources in.
 *
 * <h2>Scope</h2>
 * The module owns the lifecycle of a notebook and of everything that hangs below it. It takes
 * commands and returns domain objects, and it knows nothing about HTTP, which keeps the transport
 * layer above it free of rules and this module testable without one.
 *
 * <h2>Ownership Is a Query, Not a Check</h2>
 * Every operation receives the account it is performed for and passes it into the query. A caller
 * that names a notebook of another account gets the same answer as a caller that names a notebook
 * that does not exist, because the row is never loaded in the first place.
 *
 * <h2>Dependency Rule</h2>
 * The module depends on the domain model and on the persistence layer, and on nothing else of the
 * application. It is a sibling of the security module and follows the same shape.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.pfoertner.assessment.sumbooklm.workspace;
