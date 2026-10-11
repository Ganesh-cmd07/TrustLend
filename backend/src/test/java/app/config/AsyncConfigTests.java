package app.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTests {
    @Test
    void configuredExecutorRunsBackgroundWorkOnItsDedicatedThread() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AsyncConfig.class, AsyncTestBeans.class);
            context.refresh();
            var latch = new CountDownLatch(1);
            var threadName = new AtomicReference<String>();
            context.getBean(AsyncProbe.class).run(latch, threadName);
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(threadName.get()).startsWith("trustlend-task-");
        }
    }

    @Configuration
    static class AsyncTestBeans {
        @Bean AsyncProbe asyncProbe() { return new AsyncProbe(); }
    }

    static class AsyncProbe {
        @Async("trustLendTaskExecutor")
        public void run(CountDownLatch latch, AtomicReference<String> threadName) {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
        }
    }
}
