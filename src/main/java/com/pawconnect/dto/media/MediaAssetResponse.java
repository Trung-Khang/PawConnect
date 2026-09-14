package com.pawconnect.dto.media;

/** Metadata persisted by the owning business module; image bytes stay in Cloudinary. */
public record MediaAssetResponse(String secureUrl, String publicId) {
}
