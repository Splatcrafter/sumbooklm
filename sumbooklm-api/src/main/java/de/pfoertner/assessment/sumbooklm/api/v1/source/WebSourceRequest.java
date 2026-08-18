package de.pfoertner.assessment.sumbooklm.api.v1.source;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of a request that adds a web page to a notebook.
 *
 * <h2>What Is Checked Here</h2>
 * The pattern only establishes that the value is an HTTP address at all, which is what makes a typo
 * fail immediately with a message about the request rather than minutes later as a source that could
 * not be indexed. Whether the address may be retrieved at all is decided when it is retrieved,
 * because that decision needs to resolve the host and cannot be expressed as a pattern.
 *
 * @param url address of the page to add
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Schema(description = "Address of a web page to add as a source.")
public record WebSourceRequest(
        @Schema(description = "Address of the page to add.", example = "https://example.org/article")
        @NotBlank @Size(max = 2_000) @Pattern(regexp = "^https?://\\S+$")
        String url) {
}
