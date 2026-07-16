package com.example.backend_demo.exception;

public class VideoNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 8342067023812473923L;

	public VideoNotFoundException(String lessonId) {
        super("No video found for lesson " + lessonId);
    }
}
