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

package de.pfoertner.assessment.sumbooklm.persistence.notebook;

import de.pfoertner.assessment.sumbooklm.domain.workspace.Notebook;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadCodec;
import de.pfoertner.assessment.sumbooklm.persistence.payload.PayloadTypes;
import org.springframework.stereotype.Component;

/**
 * Assembles domain notebooks from rows and payload bytes, and payload bytes from payload objects.
 *
 * <h2>Why This Exists</h2>
 * A notebook is stored in two places at once: the columns of its row and the CBOR payload of that
 * row. Callers outside the persistence layer should not have to know which half a field lives in,
 * and this component owns that split.
 *
 * <h2>Source Count</h2>
 * The number of sources is neither a column nor part of the payload. It is counted separately and
 * handed in, because a value stored with the notebook would have to be kept in step with rows the
 * notebook does not own.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Component
public class NotebookMapper {

    /**
     * Codec used to read and write the payload column of a notebook row.
     */
    private final PayloadCodec payloadCodec;

    /**
     * Creates the mapper.
     *
     * @param payloadCodec codec for the payload column of a notebook row
     */
    public NotebookMapper(final PayloadCodec payloadCodec) {
        this.payloadCodec = payloadCodec;
    }

    /**
     * Decodes the payload of a notebook row.
     *
     * @param entity row to read the payload from
     * @return the decoded payload, migrated to the current payload schema version
     */
    public NotebookPayload readPayload(final NotebookEntity entity) {
        return this.payloadCodec.decode(
                PayloadTypes.NOTEBOOK, entity.getPayload(), entity.getPayloadVersion());
    }

    /**
     * Encodes a payload into the byte form stored in a notebook row.
     *
     * @param payload payload to encode
     * @return CBOR encoded payload at the current payload schema version
     */
    public byte[] writePayload(final NotebookPayload payload) {
        return this.payloadCodec.encode(PayloadTypes.NOTEBOOK, payload);
    }

    /**
     * Combines a row, its payload and its source count into the domain representation.
     *
     * @param entity      row to convert
     * @param sourceCount number of sources currently belonging to the notebook
     * @return the notebook as the domain model describes it
     */
    public Notebook toDomain(final NotebookEntity entity, final long sourceCount) {
        return toDomain(entity, readPayload(entity), sourceCount);
    }

    /**
     * Combines a row, an already decoded payload and a source count into the domain representation.
     *
     * @param entity      row to convert
     * @param payload     payload that belongs to the row
     * @param sourceCount number of sources currently belonging to the notebook
     * @return the notebook as the domain model describes it
     */
    public Notebook toDomain(final NotebookEntity entity, final NotebookPayload payload, final long sourceCount) {
        return new Notebook(
                entity.getId(),
                entity.getUserId(),
                payload.title(),
                payload.pinned(),
                payload.topicIcon(),
                entity.getCreatedAt(),
                entity.getLastActivityAt(),
                sourceCount);
    }
}
