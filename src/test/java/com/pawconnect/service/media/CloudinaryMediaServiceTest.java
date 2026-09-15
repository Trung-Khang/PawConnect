package com.pawconnect.service.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.pawconnect.config.CloudinaryProperties;
import com.pawconnect.dto.media.MediaAssetResponse;
import com.pawconnect.exception.InvalidMediaException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

class CloudinaryMediaServiceTest {

    @Test
    void uploadsValidatedImageAndReturnsCloudinaryMetadata() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), any())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/pawconnect/image/upload/dog.png",
                "public_id", "pawconnect/products/product-1"));

        CloudinaryMediaService service = new CloudinaryMediaService(provider(cloudinary), properties());
        MediaAssetResponse result = service.upload(png(), new MediaLocation("pawconnect/products", "product-1"));

        assertThat(result.secureUrl()).startsWith("https://");
        assertThat(result.publicId()).isEqualTo("pawconnect/products/product-1");
    }

    @Test
    void rejectsUnsupportedOrInvalidFilesBeforeCallingCloudinary() {
        CloudinaryMediaService service = new CloudinaryMediaService(provider(mock(Cloudinary.class)), properties());
        MockMultipartFile svg = new MockMultipartFile("file", "image.svg", "image/svg+xml", "<svg/>".getBytes());

        assertThatThrownBy(() -> service.upload(svg, new MediaLocation("pawconnect/products", "product-1")))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("Only JPEG, PNG, and GIF");
    }

    private CloudinaryProperties properties() {
        return new CloudinaryProperties(true, "cloud", "key", "secret", 5_242_880, 4096);
    }

    private ObjectProvider<Cloudinary> provider(Cloudinary cloudinary) {
        @SuppressWarnings("unchecked")
        ObjectProvider<Cloudinary> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(cloudinary);
        return provider;
    }

    private MockMultipartFile png() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "dog.png", "image/png", output.toByteArray());
    }
}
