package com.pawconnect.dto.media;

/** The caller persists new metadata, then removes previousPublicId after its database update succeeds. */
public record MediaReplacementResponse(MediaAssetResponse asset, String previousPublicId) {
}
