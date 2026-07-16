package com.example.backend_demo.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;

import com.example.backend_demo.config.VideoStorageProperties;
import com.example.backend_demo.entity.LessonVideo;
import com.example.backend_demo.entity.RenditionSpec;
import com.example.backend_demo.entity.RenditionStatus;
import com.example.backend_demo.entity.VideoPhase;
import com.example.backend_demo.exception.VideoNotFoundException;
import com.example.backend_demo.exception.VideoTranscodingException;
import com.example.backend_demo.repository.VideoRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;


@Service
@RequiredArgsConstructor
public class VideoService {
	private static final Logger log = LoggerFactory.getLogger(VideoService.class);
	 
    private final VideoRepository repository;
    private final FfmpegTranscodingService transcodingService;
    private final VideoStorageProperties props;

 
    /**
     * Saves the uploaded source file to disk, persists the initial
     * LessonVideo record (all renditions QUEUED), and kicks off the
     * transcoding pipeline in the background. Returns as soon as the
     * record is saved — the caller polls /video/status for progress.
     */
    public Mono<LessonVideo> handleUpload(String lessonId, FilePart filePart) {
        Path lessonUploadDir = props.uploadDir().resolve(lessonId);
        Path targetPath = lessonUploadDir.resolve(filePart.filename());
 
        return deleteExistingVideo(lessonId)
                .then(ensureDirectory(lessonUploadDir))
                .then(filePart.transferTo(targetPath))
                .then(sizeOf(targetPath))
                .flatMap(size -> repository.save(
                        LessonVideo.newlyUploaded(lessonId, filePart.filename(), size, targetPath.toString())))
                .doOnNext(saved -> runPipeline(saved)
                        .subscribe(
                                v -> log.info("Transcoding pipeline finished for lesson {}", lessonId),
                                err -> log.error("Transcoding pipeline failed for lesson {}", lessonId, err)
                        ));
    }
 
    /**
     * Wipes any previous video for this lesson — its Mongo record, the raw
     * upload directory, and every generated HLS rendition — so a re-upload
     * fully replaces rather than accumulates alongside the old one.
     *
     * Known limitation: if the previous video is still mid-transcode when a
     * replacement is uploaded, its ffmpeg process (a blocking OS process on
     * a boundedElastic thread) isn't forcibly killed here — it'll keep
     * running against directories this deletes out from under it and will
     * fail on its next write, which is caught and logged, but it's not an
     * immediate, clean cancellation.
     */
    private Mono<Void> deleteExistingVideo(String lessonId) {
        Path lessonUploadDir = props.uploadDir().resolve(lessonId);
        Path lessonHlsDir = props.hlsOutputDir().resolve(lessonId);
 
        return repository.findFirstByLessonIdOrderByCreatedAtDesc(lessonId)
                .flatMap(existing -> deleteDirectory(lessonUploadDir)
                        .then(deleteDirectory(lessonHlsDir))
                        .then(repository.deleteById(existing.id())))
                .then();
    }
 
    private Mono<Void> deleteDirectory(Path dir) {
        return Mono.<Void>fromRunnable(() -> {
                    if (!Files.exists(dir)) return;
                    try (Stream<Path> walk = Files.walk(dir)) {
                        walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Could not delete {} while replacing video", path, e);
                            }
                        });
                    } catch (IOException e) {
                        log.warn("Could not walk {} for deletion while replacing video", dir, e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
 
    public Mono<LessonVideo> getStatus(String lessonId) {
        return repository.findFirstByLessonIdOrderByCreatedAtDesc(lessonId)
                .switchIfEmpty(Mono.error(new VideoNotFoundException(lessonId)));
    }
    
    /**
     * Resolves an HLS asset (playlist.m3u8, {rendition}/playlist.m3u8, or a .ts segment)
     * under this lesson's output directory, rejecting any path that escapes it.
     */
    public Path resolveHlsAsset(String lessonId, String relativePath) {
    	String result = "";
    	if (relativePath != null && !relativePath.isEmpty()) {
    	    result= relativePath.replaceFirst("^.", "");
    	    System.out.println(result); // Output: ello
    	}
        Path lessonDir = props.hlsOutputDir().resolve(lessonId).normalize();
        Path resolved = lessonDir.resolve(result).normalize();
        System.out.println("resolved path:"+resolved.toAbsolutePath().toString());
        if (!resolved.startsWith(lessonDir)) {
            throw new SecurityException("Rejected path outside lesson directory: " + result);
        }
        return resolved;
    }
 
    private Mono<Void> runPipeline(LessonVideo video) {
        Path sourceFile = Path.of(video.sourcePath());
        Path lessonOutputDir = props.hlsOutputDir().resolve(video.lessonId());
 
        return Flux.fromIterable(RenditionSpec.DEFAULTS)
                .concatMap(spec -> encodeOneRendition(video.id(), sourceFile, lessonOutputDir, spec))
                .then(transcodingService.writeMasterPlaylist(lessonOutputDir, RenditionSpec.DEFAULTS))
                .then(finalizeAsReady(video.id(), video.lessonId()))
                .onErrorResume(ex -> markFailed(video.id(), ex))
                .then();
    }
 
    private Mono<Void> encodeOneRendition(String videoId, Path sourceFile, Path lessonOutputDir, RenditionSpec spec) {
        return updateRenditionStatus(videoId, spec.id(), RenditionStatus.ENCODING)
                .then(transcodingService.transcodeRendition(sourceFile, lessonOutputDir, spec))
                .then(updateRenditionStatus(videoId, spec.id(), RenditionStatus.DONE))
                .then();
    }
 
    private Mono<LessonVideo> updateRenditionStatus(String videoId, String renditionId, RenditionStatus status) {
        return repository.findById(videoId)
                .map(video -> video.withRenditionStatus(renditionId, status))
                .flatMap(repository::save);
    }
 
    private Mono<LessonVideo> finalizeAsReady(String videoId, String lessonId) {
        String playbackUrl = props.publicHlsBasePath() + "/" + lessonId + "/video/master.m3u8";
        return repository.findById(videoId)
                .map(video -> video.withPhase(VideoPhase.READY).withPlaybackUrl(playbackUrl))
                .flatMap(repository::save);
    }
 
    private Mono<LessonVideo> markFailed(String videoId, Throwable ex) {
        log.error("Marking video {} FAILED", videoId, ex);
        return repository.findById(videoId)
                .map(video -> video.withPhase(VideoPhase.FAILED))
                .flatMap(repository::save);
    }
 
    private Mono<Void> ensureDirectory(Path dir) {
        return Mono.<Void>fromRunnable(() -> {
                    try {
                        Files.createDirectories(dir);
                    } catch (IOException e) {
                        throw new VideoTranscodingException("Could not create upload directory " + dir, e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
 
    private Mono<Long> sizeOf(Path file) {
        return Mono.fromCallable(() -> Files.size(file))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
