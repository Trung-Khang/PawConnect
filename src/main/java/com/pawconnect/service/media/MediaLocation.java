package com.pawconnect.service.media;

public record MediaLocation(String folder, String assetKey) {
    String publicIdPrefix() {
        return folder + "/";
    }
}
