package de.pfoertner.assessment.sumbooklm.ai.chat;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises what the client built for one answer does when the answer is stopped or never begins.
 *
 * <h2>Why This Is Tested Without a Model</h2>
 * The provider is a plain HTTP server speaking the shape the client expects, because what is asserted
 * is the behaviour of the connection rather than the quality of an answer. A stop has to end the
 * exchange, and an address that answers nothing has to end the run, and neither statement needs a
 * neural network to be true.
 *
 * <h2>The Provider Reports Being Cut Off</h2>
 * The server writes parts until writing fails, and a failed write is the only way this test can see
 * that the request was abandoned rather than merely ignored. That is the difference the stop exists
 * for: a client that keeps reading and discarding leaves the provider generating an answer nobody is
 * waiting for.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
class ChatModelFactoryTest {

    /**
     * Longest a case waits for something it expects to happen quickly.
     */
    private static final long TIMEOUT_SECONDS = 30;

    /**
     * Factory under test.
     */
    private final ChatModelFactory chatModelFactory = new ChatModelFactory();

    /**
     * Engine the answers are asked for through.
     */
    private final GroundedChatEngine groundedChatEngine = new GroundedChatEngine(this.chatModelFactory);

    /**
     * Provider the questions are asked against, started per case.
     */
    private HttpServer provider;

    /**
     * Latch the provider stops writing at, so that no case leaves a thread writing forever.
     */
    private final CountDownLatch release = new CountDownLatch(1);

    /**
     * Raised by the provider when writing fails because the request was abandoned.
     */
    private final AtomicBoolean cutOff = new AtomicBoolean();

    /**
     * Creates the test class.
     */
    ChatModelFactoryTest() {
    }

    /**
     * Starts a provider that keeps writing parts of an answer.
     *
     * @throws java.io.IOException if the provider cannot be started
     */
    @BeforeEach
    void startProvider() throws java.io.IOException {
        this.provider = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.provider.setExecutor(Executors.newFixedThreadPool(2));
        this.provider.createContext("/api/chat", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream stream = exchange.getResponseBody()) {
                while (!this.release.await(20, TimeUnit.MILLISECONDS)) {
                    stream.write(part());
                    stream.flush();
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final java.io.IOException e) {
                this.cutOff.set(true);
            }
            exchange.close();
        });
        this.provider.start();
    }

    /**
     * Stops the provider and lets go of anything still writing.
     */
    @AfterEach
    void stopProvider() {
        this.release.countDown();
        this.provider.stop(0);
    }

    /**
     * Verifies that stopping an answer abandons the request, which the provider notices as a write that
     * fails, and that the run ends as finished carrying what had arrived.
     *
     * @throws InterruptedException if the waiting is interrupted
     */
    @Test
    void stoppingAnAnswerAbandonsTheRequest() throws InterruptedException {
        final AnswerCancellation cancellation = new AnswerCancellation();
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicReference<String> answer = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final CountDownLatch firstPart = new CountDownLatch(1);

        this.groundedChatEngine.answer(selection(), List.of(), List.of(), "Explain entropy.",
                new AnswerStreamHandler() {

                    @Override
                    public void onToken(final String token) {
                        firstPart.countDown();
                    }

                    @Override
                    public void onCompleted(final String text) {
                        answer.set(text);
                        finished.countDown();
                    }

                    @Override
                    public void onFailed(final Throwable error) {
                        failure.set(error);
                        finished.countDown();
                    }
                }, cancellation);

        assertThat(firstPart.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .describedAs("the answer has to begin before it can be stopped")
                .isTrue();

        cancellation.cancel();

        assertThat(finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .describedAs("a stopped answer has to end the run")
                .isTrue();
        assertThat(failure.get()).describedAs("a stopped answer is finished, not failed").isNull();
        assertThat(answer.get()).contains("tick");
        assertThat(awaitCutOff())
                .describedAs("the provider has to notice that nobody is reading what it writes")
                .isTrue();
    }

    /**
     * Verifies that an address nothing answers on ends the run as failed rather than leaving a reader
     * waiting, which is what a connection that is refused has to become.
     *
     * @throws InterruptedException if the waiting is interrupted
     */
    @Test
    void anAddressThatRefusesTheConnectionEndsTheRun() throws InterruptedException {
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        this.groundedChatEngine.answer(
                ModelSelection.of("OLLAMA", "unreachable", null, "http://127.0.0.1:9"),
                List.of(), List.of(), "Explain entropy.",
                new AnswerStreamHandler() {

                    @Override
                    public void onToken(final String token) {
                    }

                    @Override
                    public void onCompleted(final String text) {
                        finished.countDown();
                    }

                    @Override
                    public void onFailed(final Throwable error) {
                        failure.set(error);
                        finished.countDown();
                    }
                }, new AnswerCancellation());

        assertThat(finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .describedAs("a refused connection has to end the run rather than leave it open")
                .isTrue();
        assertThat(failure.get()).isNotNull();
    }

    /**
     * Waits for the provider to notice that its answer is no longer being read.
     *
     * @return {@code true} if it noticed in time
     * @throws InterruptedException if the waiting is interrupted
     */
    private boolean awaitCutOff() throws InterruptedException {
        for (int attempt = 0; attempt < TIMEOUT_SECONDS * 10 && !this.cutOff.get(); attempt += 1) {
            Thread.sleep(100);
        }
        return this.cutOff.get();
    }

    /**
     * Names the model of the provider this test started.
     *
     * @return the selection addressing the local provider
     */
    private ModelSelection selection() {
        return ModelSelection.of("OLLAMA", "writing", null,
                "http://127.0.0.1:" + this.provider.getAddress().getPort());
    }

    /**
     * Builds one part of a streamed answer in the shape the provider speaks.
     *
     * @return the encoded line
     */
    private static byte[] part() {
        return "{\"message\":{\"role\":\"assistant\",\"content\":\"tick \"},\"done\":false}\n"
                .getBytes(StandardCharsets.UTF_8);
    }
}
