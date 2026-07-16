package com.example.backend_demo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VideoStorageProperties.class)
public class VideoConfig {
}
