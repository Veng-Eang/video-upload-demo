package com.example.backend_demo.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.backend_demo.config.VideoStorageProperties;
import com.example.backend_demo.entity.RenditionSpec;
import com.example.backend_demo.exception.VideoTranscodingException;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Wraps ffmpeg CLI invocations in a reactive interface. ffmpeg itself is a
 * blocking, CPU-bound native process, so every call is pushed onto
 * Schedulers.boundedElastic() and never touches the event loop threads.
 */
@Service
public class FfmpegTranscodingService {
 
    private static final Logger log = LoggerFactory.getLogger(FfmpegTranscodingService.class);
 
    private final VideoStorageProperties props;
 
    public FfmpegTranscodingService(VideoStorageProperties props) {
        this.props = props;
    }
 
    /** Encodes a single HLS rendition (video + segments + its own playlist.m3u8). */
    public Mono<Void> transcodeRendition(Path sourceFile, Path lessonOutputDir, RenditionSpec spec) {
        return Mono.<Void>fromRunnable(() -> runFfmpeg(sourceFile, lessonOutputDir, spec))
                .subscribeOn(Schedulers.boundedElastic());
    }
 
    /** Writes the top-level playlist that references every rendition's own playlist. */
    public Mono<Void> writeMasterPlaylist(Path lessonOutputDir, List<RenditionSpec> specs) {
        return Mono.<Void>fromRunnable(() -> {
            try {
                StringBuilder sb = new StringBuilder("#EXTM3U\n");
                for (RenditionSpec spec : specs) {
                    int bandwidth = (spec.videoBitrateKbps() + spec.audioBitrateKbps()) * 1000;
                    int width = spec.height() * 16 / 9; // assume 16:9 source
                    sb.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(bandwidth)
                            .append(",RESOLUTION=").append(width).append("x").append(spec.height())
                            .append("\n")
                            .append(spec.id()).append("/playlist.m3u8\n");
                }
                Files.writeString(lessonOutputDir.resolve("master.m3u8"), sb.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new VideoTranscodingException("Failed to write master playlist", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
 
    private void runFfmpeg(Path sourceFile, Path lessonOutputDir, RenditionSpec spec) {
        Path renditionDir = lessonOutputDir.resolve(spec.id());
        try {
            Files.createDirectories(renditionDir);
        } catch (IOException e) {
            throw new VideoTranscodingException("Could not create output dir for rendition " + spec.id(), e);
        }
 
        List<String> command = buildCommand(sourceFile, renditionDir, spec);
        log.info("Starting ffmpeg for rendition {}: {}", spec.id(), String.join(" ", command));
 
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new VideoTranscodingException("Could not start ffmpeg for rendition " + spec.id(), e);
        }
 
        // Drain stdout/stderr as we go — ffmpeg writes progress to stderr continuously,
        // and an undrained pipe will eventually block the process.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[ffmpeg {}] {}", spec.id(), line);
            }
        } catch (IOException e) {
            log.warn("Error reading ffmpeg output for rendition {}", spec.id(), e);
        }
 
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new VideoTranscodingException("Interrupted while waiting for ffmpeg rendition " + spec.id(), e);
        }
 
        if (exitCode != 0) {
            throw new VideoTranscodingException("ffmpeg exited with code " + exitCode + " for rendition " + spec.id());
        }
    }
 
    private List<String> buildCommand(Path sourceFile, Path renditionDir, RenditionSpec spec) {
        List<String> cmd = new ArrayList<>();
        cmd.add(props.ffmpegBinary().toString());
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(sourceFile.toString());
        cmd.add("-vf");
        cmd.add("scale=-2:" + spec.height());
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-profile:v");
        cmd.add("main");
        cmd.add("-b:v");
        cmd.add(spec.videoBitrateKbps() + "k");
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add(spec.audioBitrateKbps() + "k");
        cmd.add("-hls_time");
        cmd.add("6");
        cmd.add("-hls_playlist_type");
        cmd.add("vod");
        cmd.add("-hls_segment_filename");
        cmd.add(renditionDir.resolve("seg_%03d.ts").toString());
        cmd.add(renditionDir.resolve("playlist.m3u8").toString());
        return cmd;
    }
}
