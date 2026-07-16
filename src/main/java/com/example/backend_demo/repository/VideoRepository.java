package com.example.backend_demo.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.example.backend_demo.entity.LessonVideo;

import reactor.core.publisher.Mono;

public interface VideoRepository extends ReactiveMongoRepository<LessonVideo, String> {
	Mono<LessonVideo> findFirstByLessonIdOrderByCreatedAtDesc(String lessonId);
}
