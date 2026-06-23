package com.devmanchego.performanceanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PerformanceAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PerformanceAnalyzerApplication.class, args);
    }
}
