package com.dgcockpit.service;

import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MinioService {

    private final MinioClient minio;

    @Value("${minio.bucket}")
    private String defaultBucket;

    @Value("${minio.url}")
    private String internalUrl;

    @Value("${minio.public-url}")
    private String publicUrl;

    public MinioService(MinioClient minio) {
        this.minio = minio;
    }

    @PostConstruct
    void init() throws Exception {
        ensureBucket(defaultBucket);
        ensureBucket("ged-signatures");
        ensureBucket("ged-stamps");
        ensureBucket("ged-documents");
        ensureBucket("ged-final-documents");
    }

    // ── Bucket management ────────────────────────────────────────────────────

    public void ensureBucket(String bucket) throws Exception {
        boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    // ── Upload MultipartFile (bucket par défaut) ──────────────────────────────

    public String upload(MultipartFile file) throws Exception {
        String name = UUID.randomUUID() + "_" + file.getOriginalFilename();
        minio.putObject(PutObjectArgs.builder()
                .bucket(defaultBucket)
                .object(name)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());
        return name;
    }

    // ── Upload bytes bruts dans un bucket spécifique ─────────────────────────

    public void uploadBytes(String bucket, String objectKey, byte[] data, String contentType) throws Exception {
        ensureBucket(bucket);
        try (InputStream is = new ByteArrayInputStream(data)) {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(is, data.length, -1)
                    .contentType(contentType)
                    .build());
        }
    }

    // ── Télécharger un objet en bytes ─────────────────────────────────────────

    public byte[] downloadBytes(String bucket, String objectKey) throws Exception {
        try (InputStream is = minio.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return is.readAllBytes();
        }
    }

    // ── URL présignée — générée via le client interne, puis le hostname est remplacé
    //    par l'URL publique pour que le navigateur puisse charger la ressource ──────

    public String presignedUrl(String objectName) throws Exception {
        String url = minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(defaultBucket)
                .object(objectName)
                .method(Method.GET)
                .expiry(1, TimeUnit.HOURS)
                .build());
        return url.replace(internalUrl, publicUrl);
    }

    public String presignedUrl(String bucket, String objectKey) throws Exception {
        String url = minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .method(Method.GET)
                .expiry(1, TimeUnit.HOURS)
                .build());
        return url.replace(internalUrl, publicUrl);
    }

    // ── Suppression ───────────────────────────────────────────────────────────

    public void delete(String bucket, String objectKey) throws Exception {
        minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    }
}
