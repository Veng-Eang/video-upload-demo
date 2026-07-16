package com.example.backend_demo.entity;


public record VideoRendition(String id, String label, String sublabel, RenditionStatus status) {

    public static VideoRendition queuedFrom(RenditionSpec spec) {
        return new VideoRendition(spec.id(), spec.label(), spec.sublabel(), RenditionStatus.QUEUED);
    }

    public VideoRendition withStatus(RenditionStatus newStatus) {
        return new VideoRendition(id, label, sublabel, newStatus);
    }
}
