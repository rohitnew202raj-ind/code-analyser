package com.acme.order;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderBatchConfig {

    @Bean
    public Job importOrdersJob(JobRepository jobRepository, Step importOrdersStep) {
        return new JobBuilder("importOrdersJob", jobRepository)
                .start(importOrdersStep)
                .build();
    }

    @Bean
    public Step importOrdersStep(JobRepository jobRepository) {
        return new StepBuilder("importOrdersStep", jobRepository)
                .tasklet(null, null)
                .build();
    }
}
