package com.example.backend_demo.entity;

/**
 * Lifecycle of a lesson video after the source file has been received.
 * There is no UPLOADING phase here on purpose — the HTTP POST itself *is*
 * the upload, so by the time a LessonVideo document exists the file is
 * already on disk and transcoding is starting or done.
 */
public enum VideoPhase {
    TRANSCODING,
    READY,
    FAILED
}
