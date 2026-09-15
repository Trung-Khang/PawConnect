package com.pawconnect.service.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.pawconnect.config.CloudinaryProperties;
import com.pawconnect.dto.media.MediaAssetResponse;
import com.pawconnect.exception.InvalidMediaException;
import com.pawconnect.exception.MediaStorageUnavailableException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CloudinaryMediaService {

    private static final long DEFAULT_MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int DEFAULT_MAX_DIMENSION = 4096;

    private final ObjectProvider<Cloudinary> cloudinaryProvider;
    private final CloudinaryProperties properties;

    public CloudinaryMediaService(ObjectProvider<Cloudinary> cloudinaryProvider, CloudinaryProperties properties) {
        this.cloudinaryProvider = cloudinaryProvider;
        this.properties = properties;
    }

    public MediaAssetResponse upload(MultipartFile file, MediaLocation location) {
        validateImage(file);
        Cloudinary cloudinary = cloudinaryProvider.getIfAvailable();
        if (cloudinary == null) {
            throw new MediaStorageUnavailableException("Cloudinary is not enabled for this environment");
        }
        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "resource_type", "image",
                    "folder", location.folder(),
                    "public_id", location.assetKey(),
                    "overwrite", false,
                    "unique_filename", false,
                    "secure", true));
            String secureUrl = stringResult(result, "secure_url");
            String publicId = stringResult(result, "public_id");
            if (!secureUrl.startsWith("https://")) {
                throw new MediaStorageUnavailableException("Cloudinary did not return a secure image URL");
            }
            return new MediaAssetResponse(secureUrl, publicId);
        } catch (IOException exception) {
            throw new MediaStorageUnavailableException("Cloudinary upload failed", exception);
        }
    }

    public void delete(String publicId) {
        Cloudinary cloudinary = cloudinaryProvider.getIfAvailable();
        if (cloudinary == null) {
            throw new MediaStorageUnavailableException("Cloudinary is not enabled for this environment");
        }
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image", "invalidate", true));
            String status = String.valueOf(result.get("result"));
            if (!"ok".equals(status) && !"not found".equals(status)) {
                throw new MediaStorageUnavailableException("Cloudinary could not delete the image");
            }
        } catch (IOException exception) {
            throw new MediaStorageUnavailableException("Cloudinary deletion failed", exception);
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidMediaException("An image file is required");
        }
        if (!("image/jpeg".equals(file.getContentType()) || "image/png".equals(file.getContentType()) || "image/gif".equals(file.getContentType()))) {
            throw new InvalidMediaException("Only JPEG, PNG, and GIF images are allowed");
        }
        if (file.getSize() > maxFileSize()) {
            throw new InvalidMediaException("Image exceeds the allowed file size");
        }
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new InvalidMediaException("The uploaded file is not a valid raster image");
            }
            if (image.getWidth() > maxDimension() || image.getHeight() > maxDimension()) {
                throw new InvalidMediaException("Image dimensions exceed the allowed limit");
            }
        } catch (IOException exception) {
            throw new InvalidMediaException("The uploaded image cannot be read");
        }
    }

    private String stringResult(Map<?, ?> result, String key) {
        Object value = result.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new MediaStorageUnavailableException("Cloudinary response is missing " + key);
        }
        return text;
    }

    private long maxFileSize() {
        return properties.maxFileSizeBytes() > 0 ? properties.maxFileSizeBytes() : DEFAULT_MAX_FILE_SIZE;
    }

    private int maxDimension() {
        return properties.maxImageDimension() > 0 ? properties.maxImageDimension() : DEFAULT_MAX_DIMENSION;
    }
}
