package com.dgcockpit.controller;

import com.dgcockpit.entity.PageAnnotation;
import com.dgcockpit.repository.PageAnnotationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class AnnotationController {

    private final PageAnnotationRepository repo;

    public AnnotationController(PageAnnotationRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/api/pages/{pageId}/annotations")
    public List<PageAnnotation> list(@PathVariable String pageId) {
        return repo.findByPageId(pageId);
    }

    @PostMapping("/api/annotations")
    public ResponseEntity<PageAnnotation> create(@RequestBody Map<String, Object> body) {
        PageAnnotation ann = fromBody(new PageAnnotation(), body);
        ann.setCreatedBy("admin");
        return ResponseEntity.ok(repo.save(ann));
    }

    @PutMapping("/api/annotations/{id}")
    public ResponseEntity<PageAnnotation> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        PageAnnotation ann = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Annotation not found: " + id));
        fromBody(ann, body);
        ann.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(repo.save(ann));
    }

    @DeleteMapping("/api/annotations/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private PageAnnotation fromBody(PageAnnotation ann, Map<String, Object> body) {
        if (body.containsKey("pageId")) ann.setPageId((String) body.get("pageId"));
        if (body.containsKey("annotationType")) ann.setAnnotationType((String) body.get("annotationType"));
        if (body.containsKey("signatureAssetId")) ann.setSignatureAssetId((String) body.get("signatureAssetId"));
        if (body.containsKey("textContent")) ann.setTextContent((String) body.get("textContent"));
        if (body.containsKey("xPercent")) ann.setXPercent(toDouble(body.get("xPercent")));
        if (body.containsKey("yPercent")) ann.setYPercent(toDouble(body.get("yPercent")));
        if (body.containsKey("widthPercent")) ann.setWidthPercent(toDouble(body.get("widthPercent")));
        if (body.containsKey("heightPercent")) ann.setHeightPercent(toDouble(body.get("heightPercent")));
        return ann;
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString());
    }
}
