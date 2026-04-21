package com.fashionrental.inventory.storage;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
@Profile("!dev")
public class CloudflareR2ImageStorageService implements ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(CloudflareR2ImageStorageService.class);

    private final S3Client s3Client;

    @Value("${app.storage.r2.bucket-name}")
    private String bucketName;

    @Value("${app.storage.r2.public-url-base}")
    private String publicUrlBase;

    @Value("${app.storage.image.full-max-px}")
    private int fullMaxPx;

    @Value("${app.storage.image.thumb-max-px}")
    private int thumbMaxPx;

    public CloudflareR2ImageStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public UploadResult uploadImage(UUID itemId, InputStream inputStream, String originalFilename, long fileSize) throws IOException {
        String fileId = UUID.randomUUID().toString();
        String fullKey = "items/" + itemId + "/" + fileId + "-full.jpg";
        String thumbKey = "items/" + itemId + "/" + fileId + "-thumb.jpg";

        byte[] fullBytes = resizeToBytes(inputStream, fullMaxPx);
        byte[] thumbBytes = resizeToBytes(new ByteArrayInputStream(fullBytes), thumbMaxPx);

        upload(fullKey, fullBytes);
        upload(thumbKey, thumbBytes);

        String fullUrl = publicUrlBase + "/" + fullKey;
        String thumbnailUrl = publicUrlBase + "/" + thumbKey;

        log.debug("Uploaded image for item {} to R2: full={}", itemId, fullKey);
        return new UploadResult(fullUrl, thumbnailUrl);
    }

    @Override
    public void deleteImage(String fullUrl, String thumbnailUrl) {
        deleteByUrl(fullUrl);
        deleteByUrl(thumbnailUrl);
    }

    private byte[] resizeToBytes(InputStream inputStream, int maxPx) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(inputStream)
                .size(maxPx, maxPx)
                .keepAspectRatio(true)
                .outputFormat("jpg")
                .toOutputStream(out);
        return out.toByteArray();
    }

    private void upload(String key, byte[] bytes) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType("image/jpeg")
                        .contentLength((long) bytes.length)
                        .build(),
                RequestBody.fromBytes(bytes)
        );
    }

    private void deleteByUrl(String url) {
        try {
            // URL format: {publicUrlBase}/items/{itemId}/{filename}
            String key = url.replace(publicUrlBase + "/", "");
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            log.debug("Deleted R2 object: {}", key);
        } catch (Exception e) {
            log.warn("Failed to delete R2 object at URL {}: {}", url, e.getMessage());
        }
    }
}
