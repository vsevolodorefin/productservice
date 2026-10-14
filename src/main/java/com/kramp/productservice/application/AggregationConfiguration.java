package com.kramp.productservice.application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AggregationProperties.class)
public class AggregationConfiguration {
    @Bean(name = "upstreamExecutor", destroyMethod = "shutdownNow")
    ExecutorService upstreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(name = "upstreamScheduler", destroyMethod = "shutdownNow")
    ScheduledExecutorService upstreamScheduler() {
        var scheduler = new ScheduledThreadPoolExecutor(2);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
