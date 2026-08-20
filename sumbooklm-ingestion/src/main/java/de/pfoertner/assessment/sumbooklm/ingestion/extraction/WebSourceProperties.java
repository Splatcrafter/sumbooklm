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

package de.pfoertner.assessment.sumbooklm.ingestion.extraction;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Externalized settings of retrieving web sources, bound from the {@code sumbooklm.ingestion.web}
 * namespace.
 *
 * <h2>Who Sets This</h2>
 * The list below belongs to whoever runs the deployment, not to whoever uses it. Naming a host in it
 * turns adding a web source from something a user decides into something an operator has permitted
 * beforehand, which is the right trade where the two are different people and the wrong one for a
 * notebook a person runs for themselves. It is therefore empty by default, and an empty list means
 * that any host is allowed to be named as long as the address it leads to is a public one.
 *
 * <h2>Exact Names</h2>
 * An entry is compared against the host of the address as a whole, ignoring case. A subdomain is
 * therefore not covered by its parent: permitting {@code example.com} permits that host and no other,
 * because a permission that spread to everything below it would also spread to whatever anybody is
 * able to have published there.
 *
 * @param allowedHosts hosts a web source may be retrieved from, empty for every public host
 * @author Erik Pförtner
 * @since 0.1.0
 */
@ConfigurationProperties("sumbooklm.ingestion.web")
public record WebSourceProperties(@DefaultValue List<String> allowedHosts) {
}
