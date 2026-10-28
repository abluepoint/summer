package com.abluepoint.summer.sample.task.infrastructure;


import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.quartz.spi.TriggerFiredBundle;

@Configuration
public class TaskQuartzConfiguration {
    @Bean
    public SchedulerFactoryBeanCustomizer taskJobFactory(ApplicationContext applicationContext) {
        return factory -> factory.setJobFactory(new SpringBeanJobFactory() {
            @Override
            protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
                Object job = super.createJobInstance(bundle);
                applicationContext.getAutowireCapableBeanFactory().autowireBean(job);
                return job;
            }
        });
    }
}
