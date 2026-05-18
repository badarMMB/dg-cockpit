package com.dgcockpit.repository;

import com.dgcockpit.entity.InstructionType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InstructionTypeRepository extends JpaRepository<InstructionType, String> {
    List<InstructionType> findAllByOrderByCategorieAscLabelAsc();
    List<InstructionType> findByActifTrueOrderByCategorieAscLabelAsc();
    boolean existsByCode(String code);
}
