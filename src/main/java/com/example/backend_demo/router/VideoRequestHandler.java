package com.example.backend_demo.router;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.example.backend_demo.config.VideoStorageProperties;
import com.example.backend_demo.entity.ResolvedAsset;
import com.example.backend_demo.exception.VideoNotFoundException;
import com.example.backend_demo.response.VideoStatusResponse;
import com.example.backend_demo.service.VideoService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class VideoRequestHandler {
	 
	    private final VideoService service;
	    private final VideoStorageProperties properties;
	 
	 
	    public Mono<ServerResponse> upload(ServerRequest request) {
	        String lessonId = request.pathVariable("lessonId");
	 
	        return request.multipartData()
	                .flatMap(parts -> {
	                    Part part = parts.getFirst("video");
	                    if (!(part instanceof FilePart filePart)) {
	                        return ServerResponse.badRequest()
	                                .contentType(MediaType.APPLICATION_JSON)
	                                .bodyValue(Map.of("error", "Expected a multipart field named 'video'"));
	                    }
	                    return service.handleUpload(lessonId, filePart)
	                            .flatMap(video -> ServerResponse.accepted()
	                                    .contentType(MediaType.APPLICATION_JSON)
	                                    .bodyValue(VideoStatusResponse.from(video)));
	                });
	    }
	    
	 // Serves master.m3u8 and .ts segments with proper content types + Range support
	    public Mono<ServerResponse> serveHlsFile(ServerRequest request) {
	        String videoId = request.pathVariable("lessonId");
	        String file = request.pathVariable("filePath");

//	        Path filePath = Paths.get(properties.hlsOutputDir().toAbsolutePath().toString(), videoId , file);
	        Path hlsAsset = service.resolveHlsAsset(videoId, file);
	        System.out.println("file path:"+hlsAsset.toAbsolutePath().toString());
	        if (!Files.exists(hlsAsset)) {
	            return ServerResponse.notFound().build();
	        }

	        MediaType mediaType = file.endsWith(".m3u8")
	            ? MediaType.valueOf("application/vnd.apple.mpegurl")
	            : MediaType.valueOf("video/MP2T");

	        var resource = new FileSystemResource(hlsAsset);

	        return ServerResponse.ok()
	            .contentType(mediaType)
	            .header(HttpHeaders.CACHE_CONTROL, "no-cache") // m3u8 shouldn't be cached aggressively
	            .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*") // adjust to your actual origin
	            .bodyValue(resource);
	    }
	    
	    /**
	     * GET /api/lessons/{lessonId}/video/{*filePath} — serves the master playlist,
	     * per-rendition playlists, and .ts segments straight from disk. filePath is
	     * everything after ".../video/", e.g. "playlist.m3u8" or "720p/seg_004.ts".
	     */
	    public Mono<ServerResponse> serveAsset(ServerRequest request) {
	        String lessonId = request.pathVariable("lessonId");
	        String filePath = request.pathVariable("filePath");
	 
	        return Mono.fromCallable(() -> {
	                    Path resolved = service.resolveHlsAsset(lessonId, filePath);
	                    System.out.println("file path:"+resolved.toAbsolutePath().toString());
	                    return new ResolvedAsset(resolved, Files.exists(resolved));
	                })
	                .subscribeOn(Schedulers.boundedElastic())
	                .flatMap(asset -> {
	                    if (!asset.exists()) {
	                        return ServerResponse.notFound().build();
	                    }
	                    Resource resource = new FileSystemResource(asset.path());
	                    return ServerResponse.ok()
	                            .contentType(mediaTypeFor(asset.path()))
//	                            .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
//	                            .header(HttpHeaders.CACHE_CONTROL, cacheControlFor(asset.path()))
	                            .body(BodyInserters.fromResource(resource));
	                });
	    }
	 
	    private record ResolvedAsset(Path path, boolean exists) {
	    }
	 
	    private MediaType mediaTypeFor(Path path) {
	        String name = path.getFileName().toString();
	        if (name.endsWith(".m3u8")) return MediaType.valueOf("application/vnd.apple.mpegurl");
	        if (name.endsWith(".ts")) return MediaType.valueOf("video/mp2t");
	        return MediaType.APPLICATION_OCTET_STREAM;
	    }
	 
	    private String cacheControlFor(Path path) {
	        // Playlists can still change while transcoding is in progress; segments never change once written.
	        return path.getFileName().toString().endsWith(".m3u8")
	                ? "no-cache"
	                : "public, max-age=31536000, immutable";
	    }
	 
	    /** GET /api/lessons/{lessonId}/video — fetch the current video (any phase), or 404 if none uploaded yet. */
	    public Mono<ServerResponse> getVideo(ServerRequest request) {
	        String lessonId = request.pathVariable("lessonId");
	 
	        return service.getStatus(lessonId)
	                .flatMap(video -> ServerResponse.ok()
	                        .contentType(MediaType.APPLICATION_JSON)
	                        .bodyValue(VideoStatusResponse.from(video)))
	                .onErrorResume(VideoNotFoundException.class, ex -> ServerResponse.notFound().build());
	    }
	 
	    /** GET /api/lessons/{lessonId}/video/status — lightweight polling target while transcoding runs. */
	    public Mono<ServerResponse> status(ServerRequest request) {
	        String lessonId = request.pathVariable("lessonId");
	 
	        return service.getStatus(lessonId)
	                .flatMap(video -> ServerResponse.ok()
	                        .contentType(MediaType.APPLICATION_JSON)
	                        .bodyValue(VideoStatusResponse.from(video)))
	                .onErrorResume(VideoNotFoundException.class, ex -> ServerResponse.notFound().build());
	    }
	}
