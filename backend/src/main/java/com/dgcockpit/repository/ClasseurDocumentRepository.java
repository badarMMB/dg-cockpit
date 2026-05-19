package com.dgcockpit.repository;

import com.dgcockpit.entity.ClasseurDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClasseurDocumentRepository extends JpaRepository<ClasseurDocument, String> {
    List<ClasseurDocument> findByClasseurIdOrderByClasseAtDesc(String classeurId);
    List<ClasseurDocument> findByDocumentId(String documentId);
    long countByClasseurId(String classeurId);
}
