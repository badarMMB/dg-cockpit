package com.dgcockpit.controller;

import com.dgcockpit.entity.UserSignatureAsset;
import com.dgcockpit.service.MinioService;
import com.dgcockpit.service.SignatureAssetService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me/signature-assets")
@CrossOrigin(origins = "*")
public class SignatureAssetController {

    private final SignatureAssetService service;
    private final MinioService minioService;

    public SignatureAssetController(SignatureAssetService service, MinioService minioService) {
        this.service = service;
        this.minioService = minioService;
    }

    @GetMapping
    public List<UserSignatureAsset> list(@RequestParam(defaultValue = "admin") String userId) {
        return service.listForUser(userId);
    }

    @PostMapping
    public ResponseEntity<UserSignatureAsset> upload(
            @RequestParam(defaultValue = "admin") String userId,
            @RequestParam String assetType,
            @RequestParam MultipartFile file) throws Exception {
        UserSignatureAsset asset = service.upload(userId, assetType, file);
        return ResponseEntity.ok(asset);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable String id) throws Exception {
        UserSignatureAsset asset = service.findById(id);
        byte[] data = minioService.downloadBytes(asset.getBucket(), asset.getObjectKey());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(data);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
