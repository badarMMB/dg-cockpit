package com.dgcockpit.repository;

import com.dgcockpit.entity.PageAnnotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PageAnnotationRepository extends JpaRepository<PageAnnotation, String> {
    List<PageAnnotation> findByPageId(String pageId);
    void deleteByPageId(String pageId);
}
