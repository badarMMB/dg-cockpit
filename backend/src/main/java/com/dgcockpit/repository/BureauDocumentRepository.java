package com.dgcockpit.repository;

import com.dgcockpit.entity.BureauDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface BureauDocumentRepository extends JpaRepository<BureauDocument, String> {
    List<BureauDocument> findBySecretaireIdOrderByCreatedAtDesc(String secretaireId);
    Optional<BureauDocument> findByPdfDocumentId(String pdfDocumentId);

    @Query("SELECT COALESCE(MAX(b.referenceNumber), 0) FROM BureauDocument b WHERE b.referenceYear = :year")
    int findMaxReferenceNumberForYear(@Param("year") int year);
}
