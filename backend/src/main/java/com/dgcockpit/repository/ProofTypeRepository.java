package com.dgcockpit.repository;

import com.dgcockpit.entity.ProofType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProofTypeRepository extends JpaRepository<ProofType, String> {
    List<ProofType> findAllByOrderByLabelAsc();
    List<ProofType> findByActifTrueOrderByLabelAsc();
}
