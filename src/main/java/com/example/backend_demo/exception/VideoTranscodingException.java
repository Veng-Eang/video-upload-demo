package com.example.backend_demo.exception;

public class VideoTranscodingException extends RuntimeException {
    private static final long serialVersionUID = 1714095986369399905L;

	public VideoTranscodingException(String message) {
        super(message);
    }
 
    public VideoTranscodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
