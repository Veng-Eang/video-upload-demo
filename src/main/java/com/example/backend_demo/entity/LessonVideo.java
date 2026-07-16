package com.example.backend_demo.entity;


import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "lesson_videos")
public record LessonVideo(
        @Id
		String id,
        String lessonId,
        String sourceFileName,
        long sourceSizeBytes,
        String sourcePath,
        VideoPhase phase,
        List<VideoRendition> renditions,
        String playbackUrl,
        Instant createdAt,
        Instant updatedAt
) {

    public static LessonVideo newlyUploaded(String lessonId, String sourceFileName, long sourceSizeBytes, String sourcePath) {
        Instant now = Instant.now();
        List<VideoRendition> queued = RenditionSpec.DEFAULTS.stream()
                .map(VideoRendition::queuedFrom)
                .toList();
        return new LessonVideo(null, lessonId, sourceFileName, sourceSizeBytes, sourcePath,
                VideoPhase.TRANSCODING, queued, null, now, now);
    }

    public LessonVideo withPhase(VideoPhase newPhase) {
        return new LessonVideo(id, lessonId, sourceFileName, sourceSizeBytes, sourcePath,
                newPhase, renditions, playbackUrl, createdAt, Instant.now());
    }

    public LessonVideo withRenditions(List<VideoRendition> newRenditions) {
        return new LessonVideo(id, lessonId, sourceFileName, sourceSizeBytes, sourcePath,
                phase, newRenditions, playbackUrl, createdAt, Instant.now());
    }

    public LessonVideo withPlaybackUrl(String url) {
        return new LessonVideo(id, lessonId, sourceFileName, sourceSizeBytes, sourcePath,
                phase, renditions, url, createdAt, Instant.now());
    }

    public LessonVideo withRenditionStatus(String renditionId, RenditionStatus status) {
        List<VideoRendition> updated = renditions.stream()
                .map(r -> r.id().equals(renditionId) ? r.withStatus(status) : r)
                .toList();
        return withRenditions(updated);
    }

    public boolean allRenditionsDone() {
        return renditions.stream().allMatch(r -> r.status() == RenditionStatus.DONE);
    }
}
