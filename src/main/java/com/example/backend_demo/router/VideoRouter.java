package com.example.backend_demo.router;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class VideoRouter {
	
	private final VideoRequestHandler handler;
    @Bean
    RouterFunction<ServerResponse> lessonVideoRoutes() {
        return RouterFunctions.route()
                .POST("/api/lessons/{lessonId}/video", handler::upload)
                .GET("/api/lessons/{lessonId}/video", handler::getVideo)
                .GET("/api/lessons/{lessonId}/video/status", handler::status)
                .GET("/api/lessons/{lessonId}/video/{*filePath}", handler::serveHlsFile)
                .filter(this::logFilter)
                .build();
    } 

	private Mono<ServerResponse> logFilter(ServerRequest request, HandlerFunction<ServerResponse> next) {
        return next.handle(request)
            .doOnNext(response -> 
                log.info("{} {} -> {}", request.method(), request.path(), response.statusCode()));
    }
}
