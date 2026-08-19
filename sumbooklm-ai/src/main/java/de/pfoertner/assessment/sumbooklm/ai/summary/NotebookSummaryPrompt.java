package de.pfoertner.assessment.sumbooklm.ai.summary;

import java.util.List;
import java.util.Locale;

/**
 * Builds the instructions a summary is written under.
 *
 * <h2>Why the Rules Are Explicit</h2>
 * A model handed a stack of documents and asked what they are about will happily supply what it
 * expects such documents to contain. The rules below are what turns that into a description of these
 * documents: they name the material as the only permitted source, they forbid the invention of a
 * detail that is merely likely, and they require the text to be about the subject rather than about
 * the act of summarising.
 *
 * <h2>No Citations</h2>
 * An answer cites, because it is a claim the reader has to be able to check against a passage. A
 * summary is read as an orientation before anything has been asked, and the numbers of an answer
 * would be links into a list that is not shown next to it. The instructions therefore forbid them,
 * which is also what keeps the text short enough for the space it stands in.
 *
 * <h2>The Language Is the Reader's</h2>
 * A summary has no question whose language it could follow, and the language of the sources is not
 * the language of the person reading them. The caller names the language they are reading the
 * application in, and it is named to the model as a language rather than as a tag, because a tag is
 * a thing to be recognised while a name is a thing to be understood. An unknown tag leaves the rule
 * out entirely, which lets the model fall back to the sources rather than to a wrong guess.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class NotebookSummaryPrompt {

    /**
     * Instruction that asks for the summary, sent as the message of the user.
     */
    public static final String REQUEST = "Summarise these sources.";

    /**
     * Rules every summary is written under.
     */
    private static final String RULES = """
            You are the assistant of a notebook. You are given the sources it holds and write one \
            short summary of them.

            Rules:
            1. Use only the material below. Never use knowledge from your training and never fill a \
            gap with a detail that is merely plausible.
            2. Say what the sources are about and what a reader would find in them. Where they cover \
            more than one subject, name the subjects.
            3. Write at most five sentences, as flowing text. Do not use headings, lists, citations \
            or source numbers.
            4. Begin with the subject. Do not open with a sentence about the notebook, about the \
            sources or about summarising them.
            5. A text marked with [...] was cut short. Summarise what is there and do not guess at \
            what followed.""";

    /**
     * Heading the sources are listed under.
     */
    private static final String SOURCES_HEADING = "Sources:";

    /**
     * Prevents instantiation of this prompt builder.
     */
    private NotebookSummaryPrompt() {
        throw new AssertionError("NotebookSummaryPrompt is a utility class and must not be instantiated");
    }

    /**
     * Builds the instructions for one summary.
     *
     * @param sources     sources the summary is written from, already cut to what fits, never empty
     * @param languageTag IETF language tag the summary is to be written in, empty for the language of
     *                    the sources
     * @return the text of the system message the model is given
     * @throws IllegalArgumentException if there are no sources, because a notebook with nothing in it
     *                                  is answered without a model rather than by asking one to
     *                                  describe nothing
     */
    public static String of(final List<SourceExcerpt> sources, final String languageTag) {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("A summary without sources must not be requested at all");
        }

        final StringBuilder prompt = new StringBuilder(RULES);
        final String language = languageName(languageTag);
        if (!language.isEmpty()) {
            prompt.append("\n6. Write the summary in ").append(language).append('.');
        }

        prompt.append("\n\n").append(SOURCES_HEADING);
        for (final SourceExcerpt source : sources) {
            prompt.append("\n\n").append(source.displayName()).append('\n').append(source.text());
        }
        return prompt.toString();
    }

    /**
     * Turns a language tag into the English name of the language it denotes.
     *
     * @param languageTag tag as the caller presented it, possibly {@code null} or unknown
     * @return the English name of the language, empty when the tag names none
     */
    private static String languageName(final String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return "";
        }
        final Locale locale = Locale.forLanguageTag(languageTag.strip());
        final String name = locale.getDisplayLanguage(Locale.ENGLISH);
        // A tag that denotes no known language is displayed as itself, which is a name for nobody.
        return name.equals(locale.getLanguage()) ? "" : name;
    }
}
