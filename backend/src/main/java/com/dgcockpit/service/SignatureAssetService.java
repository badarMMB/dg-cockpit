package com.dgcockpit.service;

import com.dgcockpit.entity.UserSignatureAsset;
import com.dgcockpit.repository.UserSignatureAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SignatureAssetService {

    private final UserSignatureAssetRepository repo;
    private final SignatureImageProcessingService processor;
    private final MinioService minio;

    public SignatureAssetService(UserSignatureAssetRepository repo,
                                 SignatureImageProcessingService processor,
                                 MinioService minio) {
        this.repo = repo;
        this.processor = processor;
        this.minio = minio;
    }

    public List<UserSignatureAsset> listForUser(String userId) {
        return repo.findByUserIdAndActiveTrue(userId);
    }

    public UserSignatureAsset upload(String userId, String assetType, MultipartFile file) throws Exception {
        if (!assetType.equals("SIGNATURE") && !assetType.equals("STAMP")) {
            throw new IllegalArgumentException("assetType must be SIGNATURE or STAMP");
        }

        byte[] processed = processor.removeBackground(file.getBytes());

        String bucket = assetType.equals("SIGNATURE") ? "ged-signatures" : "ged-stamps";
        String objectKey = UUID.randomUUID() + "_" + file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_") + ".png";

        minio.uploadBytes(bucket, objectKey, processed, "image/png");

        UserSignatureAsset asset = new UserSignatureAsset();
        asset.setUserId(userId);
        asset.setAssetType(assetType);
        asset.setBucket(bucket);
        asset.setObjectKey(objectKey);
        asset.setOriginalFileName(file.getOriginalFilename());
        asset.setContentType("image/png");
        asset.setFileSize((long) processed.length);
        return repo.save(asset);
    }

    public UserSignatureAsset findById(String assetId) {
        return repo.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));
    }

    public void deactivate(String assetId) {
        repo.findById(assetId).ifPresent(a -> {
            a.setActive(false);
            a.setUpdatedAt(LocalDateTime.now());
            repo.save(a);
        });
    }
}
