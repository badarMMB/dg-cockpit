package com.dgcockpit.controller;

import com.dgcockpit.service.DocumentFinalizationService;
import com.dgcockpit.service.MinioService;
import com.dgcockpit.sse.SseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final MinioService minioService;
    private final SseService sseService;
    private final DocumentFinalizationService finalizer;

    @Value("${minio.bucket}")
    private String defaultBucket;

    public FileController(MinioService minioService, SseService sseService, DocumentFinalizationService finalizer) {
        this.minioService = minioService;
        this.sseService = sseService;
        this.finalizer = finalizer;
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

    @GetMapping("/{objectName}/info")
    public ResponseEntity<Map<String, Object>> info(@PathVariable String objectName) throws Exception {
        int pageCount = finalizer.getPageCount(defaultBucket, objectName);
        return ResponseEntity.ok(Map.of("pageCount", pageCount, "name", objectName));
    }

    @GetMapping("/{objectName}/page/{pageIndex}")
    public ResponseEntity<byte[]> renderPage(@PathVariable String objectName,
                                             @PathVariable int pageIndex) throws Exception {
        byte[] png = finalizer.renderPageFromStorage(defaultBucket, objectName, pageIndex);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(png);
    }
}
