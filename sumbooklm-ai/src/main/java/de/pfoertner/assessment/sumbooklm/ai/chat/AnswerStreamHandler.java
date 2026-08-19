package de.pfoertner.assessment.sumbooklm.ai.chat;

/**
 * Receiver of an answer while it is being generated.
 *
 * <h2>Exactly One Ending</h2>
 * A run ends either in {@link #onCompleted(String)} or in {@link #onFailed(Throwable)}, never in
 * both and never in neither. An implementation that closes a connection may therefore do so in the
 * two ending methods without keeping track of whether the other one has already run.
 *
 * <h2>Calling Thread</h2>
 * The methods are invoked by whichever thread the provider delivers its response on, which is not the
 * thread that asked the question. An implementation that touches shared state has to say how it is
 * published.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface AnswerStreamHandler {

    /**
     * Receives the next part of the answer.
     *
     * @param token text generated since the previous call, usually a single token
     */
    void onToken(String token);

    /**
     * Receives the finished answer.
     *
     * @param answer the complete answer, which is the concatenation of everything that was streamed
     */
    void onCompleted(String answer);

    /**
     * Reports that no answer will arrive.
     *
     * @param error cause of the failure, as the provider or the client reported it
     */
    void onFailed(Throwable error);
}
