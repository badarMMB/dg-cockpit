package com.dgcockpit.repository;

import com.dgcockpit.entity.PdfDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PdfDocumentRepository extends JpaRepository<PdfDocument, String> {

    List<PdfDocument> findAllByOrderByCreatedAtDesc();

    List<PdfDocument> findByParapheurStatutOrderBySubmittedAtDesc(PdfDocument.ParapheurStatut statut);

    @Query("SELECT d FROM PdfDocument d WHERE d.parapheurStatut IN :statuts ORDER BY d.updatedAt DESC")
    List<PdfDocument> findHistorique(@Param("statuts") List<PdfDocument.ParapheurStatut> statuts);

    List<PdfDocument> findByParapheurStatutAndParapheurTypeOrderBySignedAtDesc(
            PdfDocument.ParapheurStatut statut, PdfDocument.ParapheurType type);
}
