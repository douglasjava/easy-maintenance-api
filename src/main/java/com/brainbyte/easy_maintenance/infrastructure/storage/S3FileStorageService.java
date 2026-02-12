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

import java.io.InputStream;

@Slf4j
@Service
public class S3FileStorageService {

    private final S3Client s3Client;
    private final String bucketName;

    public S3FileStorageService(S3Client s3Client, @Value("${aws.s3.bucket}") String bucketName) {
        this.s3Client = s3Client;
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

    private String extractKeyFromUrl(String fileUrl) {
        // Exemplo: https://bucket.s3.amazonaws.com/path/file.jpg -> path/file.jpg
        return fileUrl.substring(fileUrl.indexOf(".com/") + 5);
    }
}
