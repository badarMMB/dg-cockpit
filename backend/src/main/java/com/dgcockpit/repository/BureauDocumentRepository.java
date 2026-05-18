package com.dgcockpit.repository;

import com.dgcockpit.entity.BureauDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BureauDocumentRepository extends JpaRepository<BureauDocument, String> {
    List<BureauDocument> findBySecretaireIdOrderByCreatedAtDesc(String secretaireId);
}
