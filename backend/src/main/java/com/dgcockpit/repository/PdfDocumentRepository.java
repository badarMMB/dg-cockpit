package com.dgcockpit.repository;

import com.dgcockpit.entity.PdfDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PdfDocumentRepository extends JpaRepository<PdfDocument, String> {
    List<PdfDocument> findAllByOrderByCreatedAtDesc();
}
