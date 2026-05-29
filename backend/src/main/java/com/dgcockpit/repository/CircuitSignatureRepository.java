package com.dgcockpit.repository;

import com.dgcockpit.entity.CircuitSignature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CircuitSignatureRepository extends JpaRepository<CircuitSignature, String> {

    List<CircuitSignature> findByPdfDocumentIdOrderByStepOrderAsc(String pdfDocumentId);

    Optional<CircuitSignature> findFirstByPdfDocumentIdAndStatutOrderByStepOrderAsc(
            String pdfDocumentId, CircuitSignature.StatutEtape statut);

    Optional<CircuitSignature> findByPdfDocumentIdAndStepOrder(
            String pdfDocumentId, int stepOrder);
}
