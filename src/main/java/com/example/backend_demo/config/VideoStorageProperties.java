package com.example.backend_demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
 
/**
 * Bind from application.yml:
 *
 * lms:
 *   video:
 *     upload-dir: /data/lms/uploads
 *     hls-output-dir: /data/lms/hls
 *     ffmpeg-binary: /usr/bin/ffmpeg
 *     public-hls-base-path: /media/hls
 *
 * `public-hls-base-path` is the path nginx (or WebFlux static resource
 * handling) serves hls-output-dir under, used to build the playbackUrl.
 */
@ConfigurationProperties(prefix = "lms.video")
public record VideoStorageProperties(
        Path uploadDir,
        Path hlsOutputDir,
        Path ffmpegBinary,
        String publicHlsBasePath
) {
}
