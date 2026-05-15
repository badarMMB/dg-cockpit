package com.dgcockpit.controller;

import com.dgcockpit.service.MinioService;
import com.dgcockpit.sse.SseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final MinioService minioService;
    private final SseService sseService;

    public FileController(MinioService minioService, SseService sseService) {
        this.minioService = minioService;
        this.sseService = sseService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        String name = minioService.upload(file);
        sseService.broadcast("FILE_UPLOADED", Map.of("name", name));
        return ResponseEntity.ok(Map.of("name", name));
    }

    @GetMapping("/{objectName}")
    public ResponseEntity<Void> download(@PathVariable String objectName) throws Exception {
        String url = minioService.presignedUrl(objectName);
        return ResponseEntity.status(302).header("Location", url).build();
    }
}
