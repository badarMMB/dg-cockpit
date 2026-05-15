package com.dgcockpit.service;

import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MinioService {

    private final MinioClient minio;

    @Value("${minio.bucket}")
    private String bucket;

    public MinioService(MinioClient minio) {
        this.minio = minio;
    }

    @PostConstruct
    void init() throws Exception {
        boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    public String upload(MultipartFile file) throws Exception {
        String name = UUID.randomUUID() + "_" + file.getOriginalFilename();
        minio.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(name)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());
        return name;
    }

    public String presignedUrl(String objectName) throws Exception {
        return minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .method(Method.GET)
                .expiry(1, TimeUnit.HOURS)
                .build());
    }
}
