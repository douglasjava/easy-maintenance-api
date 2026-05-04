package com.brainbyte.easy_maintenance.infrastructure.storage;

import com.brainbyte.easy_maintenance.commons.exceptions.S3Exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

@Slf4j
@Service
public class S3FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;

    public S3FileStorageService(S3Client s3Client, S3Presigner s3Presigner,
                                @Value("${aws.s3.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
    }

    public String upload(String path, String fileName, String contentType, InputStream inputStream, long size) {
        String key = path + "/" + fileName;
        log.info("Uploading file to S3: {}/{}", bucketName, key);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, size));

            return String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);
        } catch (Exception e) {
            log.error("Error uploading file to S3", e);
            throw new S3Exception("Failed to upload file to S3", e);
        }
    }

    public InputStream download(String fileUrl) {
        String key = extractKeyFromUrl(fileUrl);
        log.info("Downloading file from S3: {}/{}", bucketName, key);

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObject(getObjectRequest);
        } catch (Exception e) {
            log.error("Error downloading file from S3", e);
            throw new S3Exception("Failed to download file from S3", e);
        }
    }

    public void delete(String fileUrl) {
        String key = extractKeyFromUrl(fileUrl);
        log.info("Deleting file from S3: {}/{}", bucketName, key);

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
        } catch (Exception e) {
            log.error("Error deleting file from S3", e);
        }
    }

    public String generatePresignedPutUrl(String key, String contentType) {
        log.info("Generating presigned PUT URL for S3 key: {}", key);
        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15))
                    .putObjectRequest(objectRequest)
                    .build();

            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
            return presigned.url().toString();
        } catch (Exception e) {
            log.error("Error generating presigned PUT URL for key: {}", key, e);
            throw new S3Exception("Failed to generate presigned upload URL", e);
        }
    }

    public String buildFileUrl(String key) {
        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);
    }

    private String extractKeyFromUrl(String fileUrl) {
        // Exemplo: https://bucket.s3.amazonaws.com/path/file.jpg -> path/file.jpg
        return fileUrl.substring(fileUrl.indexOf(".com/") + 5);
    }
}
