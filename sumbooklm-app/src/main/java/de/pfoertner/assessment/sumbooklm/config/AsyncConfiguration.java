package de.pfoertner.assessment.sumbooklm.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import de.pfoertner.assessment.sumbooklm.workspace.chat.NotebookChatService;
import de.pfoertner.assessment.sumbooklm.workspace.source.SourceIngestionPipeline;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables background execution and provides the executors background work is performed on.
 *
 * <h2>Why It Lives Here</h2>
 * Switching background execution on is a decision about the whole application rather than about one
 * feature, and how much of the machine background work may occupy is a deployment concern. Both are
 * therefore made by the composition root, as the clock is, while the module that needs the executor
 * only names it.
 *
 * <h2>Sizing</h2>
 * Indexing is bounded by computing embeddings, which saturates the cores it is given. A small pool
 * with a bounded queue is therefore the right shape: more threads would not index faster, and an
 * unbounded queue would accept work until the heap gave out. Once the queue is full the caller runs
 * the task itself, which slows uploads down instead of discarding one.
 *
 * <h2>Two Pools, Not One</h2>
 * Indexing and answering are given separate pools because they are limited by different things.
 * Indexing computes embeddings and is bounded by the cores of the machine, while answering waits for
 * a foreign server and occupies a thread without using it. Sharing one pool would let a burst of
 * uploads delay every answer, and a slow provider block every upload.
 *
 * <h2>Separate From the Server</h2>
 * Neither pool is the one the web server serves requests from. An indexing run that takes a minute
 * and an answer that takes half of one therefore cannot occupy a thread that a request is waiting
 * for.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfiguration {

    /**
     * Number of indexing runs that are performed at the same time.
     */
    private static final int INGESTION_POOL_SIZE = 2;

    /**
     * Number of indexing runs that wait before the caller is made to run one itself.
     */
    private static final int INGESTION_QUEUE_CAPACITY = 64;

    /**
     * Prefix of the indexing thread names, so that a stack trace says which pool it came from.
     */
    private static final String INGESTION_THREAD_NAME_PREFIX = "ingestion-";

    /**
     * Number of answers that are generated at the same time.
     */
    private static final int CHAT_POOL_SIZE = 8;

    /**
     * Number of questions that wait before the caller is made to answer one itself.
     */
    private static final int CHAT_QUEUE_CAPACITY = 32;

    /**
     * Prefix of the answering thread names, so that a stack trace says which pool it came from.
     */
    private static final String CHAT_THREAD_NAME_PREFIX = "chat-";

    /**
     * Creates the configuration. The instance is created by the container and holds no state.
     */
    public AsyncConfiguration() {
    }

    /**
     * Provides the executor indexing runs are performed on.
     *
     * @return a bounded pool that makes the caller run the task once its queue is full
     */
    @Bean(SourceIngestionPipeline.INGESTION_EXECUTOR)
    public Executor sourceIngestionExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(INGESTION_POOL_SIZE);
        executor.setMaxPoolSize(INGESTION_POOL_SIZE);
        executor.setQueueCapacity(INGESTION_QUEUE_CAPACITY);
        executor.setThreadNamePrefix(INGESTION_THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /**
     * Provides the executor answers are generated on.
     *
     * <p>The pool is not waited for while the application shuts down. A generated answer is streamed
     * to a connection that is closing with the server anyway, and holding the shutdown for a foreign
     * provider that has not replied yet would delay it by the request timeout.
     *
     * @return a bounded pool that makes the caller generate the answer once its queue is full
     */
    @Bean(NotebookChatService.CHAT_EXECUTOR)
    public Executor chatAnswerExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CHAT_POOL_SIZE);
        executor.setMaxPoolSize(CHAT_POOL_SIZE);
        executor.setQueueCapacity(CHAT_QUEUE_CAPACITY);
        executor.setThreadNamePrefix(CHAT_THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
