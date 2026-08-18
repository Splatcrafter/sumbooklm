package de.pfoertner.assessment.sumbooklm.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import de.pfoertner.assessment.sumbooklm.workspace.source.SourceIngestionPipeline;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables background execution and provides the executor indexing runs use.
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
 * <h2>Separate From the Server</h2>
 * The pool is not the one the web server serves requests from. An indexing run that takes a minute
 * therefore cannot occupy a thread that a request is waiting for.
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
    private static final int POOL_SIZE = 2;

    /**
     * Number of indexing runs that wait before the caller is made to run one itself.
     */
    private static final int QUEUE_CAPACITY = 64;

    /**
     * Prefix of the thread names, so that a stack trace says which pool it came from.
     */
    private static final String THREAD_NAME_PREFIX = "ingestion-";

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
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
