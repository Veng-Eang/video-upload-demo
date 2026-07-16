package com.example.backend_demo.response;

import java.util.List;

import com.example.backend_demo.entity.LessonVideo;
import com.example.backend_demo.entity.VideoPhase;
import com.example.backend_demo.entity.VideoRendition;

public record VideoStatusResponse(
        String fileName,
        List<RenditionDto> renditions,
        String playbackUrl,
        VideoPhase phase
) {
 
    public record RenditionDto(String id, String label, String sublabel, String status) {
        static RenditionDto from(VideoRendition r) {
            // lowercase to match the Angular RenditionStatus union: 'queued' | 'encoding' | 'done'
            return new RenditionDto(r.id(), r.label(), r.sublabel(), r.status().name().toLowerCase());
        }
    }
 
    public static VideoStatusResponse from(LessonVideo video) {
        List<RenditionDto> renditions = video.renditions().stream()
                .map(RenditionDto::from)
                .toList();
        return new VideoStatusResponse(video.sourceFileName(), renditions, video.playbackUrl(), video.phase());
    }
}