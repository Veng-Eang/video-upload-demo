package com.example.backend_demo.entity;


import java.util.List;

/**
 * Encoding profile for a single HLS rendition. Kept separate from
 * {@link VideoRendition} (the persisted state) so the ffmpeg parameters
 * live in one place and aren't duplicated into MongoDB.
 */
public record RenditionSpec(
        String id,
        String label,
        String sublabel,
        int height,
        int videoBitrateKbps,
        int audioBitrateKbps
) {
    public static final List<RenditionSpec> DEFAULTS = List.of(
            new RenditionSpec("1080p", "1080p", "high", 1080, 5000, 192),
            new RenditionSpec("720p", "720p", "standard", 720, 2800, 160),
            new RenditionSpec("480p", "480p", "saver", 480, 1400, 128),
            new RenditionSpec("240p", "240p", "low bandwidth", 240, 600, 96)
    );
}
