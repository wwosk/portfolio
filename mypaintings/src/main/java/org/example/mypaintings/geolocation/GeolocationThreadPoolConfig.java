package org.example.mypaintings.geolocation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;


@Configuration
@EnableAsync
@EnableRetry
public class GeolocationThreadPoolConfig {

    @Bean(name = "locationTaskExecutor")
    public ThreadPoolTaskExecutor locationTaskExecutor(
            @Value("${app.scheduler.location-threads:4}") int threadCount) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadCount);
        executor.setMaxPoolSize(threadCount);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("LocationExec-");
        executor.initialize();
        return executor;
    }
}