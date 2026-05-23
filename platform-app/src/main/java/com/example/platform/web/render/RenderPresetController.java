package com.example.platform.web.render;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/render")
@Tag(name = "Render", description = "Video rendering and export presets/profiles")
public class RenderPresetController {

    @GetMapping("/presets")
    @Operation(summary = "List available render presets",
               description = "Returns all available render presets with tier availability information")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved render presets")
    })
    public ResponseEntity<Map<String, Object>> getPresets() {
        List<Map<String, Object>> presets = List.of(
                Map.of("id", "720p", "name", "HD 720p", "resolution", "1280x720",
                        "fps", 30, "codec", "h264", "tier", "free",
                        "description", "Standard HD quality suitable for web sharing"),
                Map.of("id", "1080p", "name", "Full HD 1080p", "resolution", "1920x1080",
                        "fps", 30, "codec", "h264", "tier", "pro",
                        "description", "Full HD quality for professional content"),
                Map.of("id", "1080p60", "name", "Full HD 1080p 60fps", "resolution", "1920x1080",
                        "fps", 60, "codec", "h264", "tier", "pro",
                        "description", "Full HD at 60fps for smooth motion"),
                Map.of("id", "4k", "name", "4K Ultra HD", "resolution", "3840x2160",
                        "fps", 30, "codec", "h265", "tier", "enterprise",
                        "description", "4K Ultra HD quality for premium content"),
                Map.of("id", "4k60", "name", "4K Ultra HD 60fps", "resolution", "3840x2160",
                        "fps", 60, "codec", "h265", "tier", "enterprise",
                        "description", "4K at 60fps for premium smooth content"),
                Map.of("id", "vertical_1080", "name", "Vertical 1080x1920", "resolution", "1080x1920",
                        "fps", 30, "codec", "h264", "tier", "free",
                        "description", "Vertical format for mobile/social media (9:16)"),
                Map.of("id", "square_1080", "name", "Square 1080x1080", "resolution", "1080x1080",
                        "fps", 30, "codec", "h264", "tier", "free",
                        "description", "Square format for Instagram and social feeds")
        );
        return ResponseEntity.ok(Map.of("presets", presets, "total", presets.size()));
    }

    @GetMapping("/profiles")
    @Operation(summary = "List available render profiles",
               description = "Returns all available render profiles with configuration details")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved render profiles")
    })
    public ResponseEntity<Map<String, Object>> getProfiles() {
        List<Map<String, Object>> profiles = List.of(
                Map.of("id", "web_optimized", "name", "Web Optimized",
                        "preset", "1080p", "bitrate", "8Mbps",
                        "audioCodec", "aac", "audioBitrate", "128kbps",
                        "description", "Optimized for web streaming and fast loading"),
                Map.of("id", "social_media", "name", "Social Media",
                        "preset", "1080p", "bitrate", "6Mbps",
                        "audioCodec", "aac", "audioBitrate", "128kbps",
                        "description", "Balanced quality for social media platforms"),
                Map.of("id", "archive", "name", "Archive Quality",
                        "preset", "4k", "bitrate", "50Mbps",
                        "audioCodec", "aac", "audioBitrate", "320kbps",
                        "description", "High quality for long-term archival"),
                Map.of("id", "draft", "name", "Draft Preview",
                        "preset", "720p", "bitrate", "4Mbps",
                        "audioCodec", "aac", "audioBitrate", "96kbps",
                        "description", "Fast rendering for preview and review"),
                Map.of("id", "mobile", "name", "Mobile Optimized",
                        "preset", "720p", "bitrate", "3Mbps",
                        "audioCodec", "aac", "audioBitrate", "96kbps",
                        "description", "Small file size for mobile devices")
        );
        return ResponseEntity.ok(Map.of("profiles", profiles, "total", profiles.size()));
    }
}
