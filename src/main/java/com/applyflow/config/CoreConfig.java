package com.applyflow.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class CoreConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Zone used for user-facing day boundaries (weeks, "today", parsing times without an explicit zone). */
    @Bean
    public ZoneId appZone(AppProperties props) {
        try {
            return ZoneId.of(props.timezone());
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        // Nulls are always serialized (no NON_NULL inclusion), dates as ISO-8601 strings.
        return builder -> builder
                .modulesToInstall(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** Bounded executor for mailbox synchronisation. */
    @Bean(name = "syncExecutor")
    public ThreadPoolTaskExecutor syncExecutor(AppProperties props) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        int threads = Math.max(1, props.sync().executorThreads());
        ex.setCorePoolSize(threads);
        ex.setMaxPoolSize(threads);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("mail-sync-");
        ex.setWaitForTasksToCompleteOnShutdown(false);
        ex.initialize();
        return ex;
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.initialize();
        return scheduler;
    }

    /** Transaction template used to process each email in its own transaction. */
    @Bean
    public TransactionTemplate requiresNewTransactionTemplate(PlatformTransactionManager txManager) {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tt;
    }
}
