package com.dgcockpit.repository;

import com.dgcockpit.entity.TypeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TypeDocumentRepository extends JpaRepository<TypeDocument, String> {

    List<TypeDocument> findByActifTrueOrderByLibelleAsc();

    List<TypeDocument> findAllByOrderByLibelleAsc();

    Optional<TypeDocument> findByCode(String code);
}
