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

package de.pfoertner.assessment.sumbooklm.security.authentication;

/**
 * Request to create an account.
 *
 * <h2>Network Address</h2>
 * The address is not part of what a client sends. It is determined by the transport layer from the
 * connection and passed in here, so that the security module records where an account was created
 * from without depending on the servlet API.
 *
 * @param username  login name the account is created under, unique across all accounts
 * @param firstName given name of the user
 * @param lastName  family name of the user
 * @param password  clear text password, hashed before it is stored and never retained
 * @param ipAddress network address the registration was requested from
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record RegistrationCommand(String username,
                                  String firstName,
                                  String lastName,
                                  String password,
                                  String ipAddress) {
}
