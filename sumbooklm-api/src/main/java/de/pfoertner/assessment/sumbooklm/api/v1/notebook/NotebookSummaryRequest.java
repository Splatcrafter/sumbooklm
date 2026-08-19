package de.pfoertner.assessment.sumbooklm.api.v1.notebook;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of a request that has the summary of a notebook written.
 *
 * <h2>Why the Language Is Sent</h2>
 * An answer is written in the language of the question. A summary has no question, and the language
 * of the sources is not necessarily the language of the reader, so the client names the one it is
 * being read in. It is the only thing this request carries: what is summarised follows from the
 * notebook in the path, and the model follows from the headers.
 *
 * <h2>Shape Rather Than List</h2>
 * The value is checked against the shape of a language tag rather than against the languages this
 * application is translated into. The tag reaches a model as the name of a language, and a model
 * writes in more languages than an interface is translated into, so refusing one because no
 * translation exists for it would be this layer deciding something it does not own.
 *
 * @param language IETF language tag the summary is to be written in, absent for the language of the
 *                 sources
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "How the summary of a notebook is to be written.")
public record NotebookSummaryRequest(
        @Schema(description = "IETF language tag the summary is to be written in.", example = "de")
        @Size(max = 35)
        @Pattern(regexp = "|[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")
        String language) {

    /**
     * Returns the language tag the caller presented, with an absent value read as none.
     *
     * @return the language tag, empty when the caller named none
     */
    public String languageOrEmpty() {
        return this.language == null ? "" : this.language;
    }
}
