package com.literag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import com.literag.config.AppProperties;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class LiteRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiteRagApplication.class, args);
    }
}
