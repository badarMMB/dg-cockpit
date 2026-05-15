package com.dgcockpit.controller;

import com.dgcockpit.entity.UserSignatureAsset;
import com.dgcockpit.service.SignatureAssetService;
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

    public SignatureAssetController(SignatureAssetService service) {
        this.service = service;
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

    @GetMapping("/{id}/url")
    public ResponseEntity<Map<String, String>> getUrl(@PathVariable String id) throws Exception {
        String url = service.getPresignedUrl(id);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
