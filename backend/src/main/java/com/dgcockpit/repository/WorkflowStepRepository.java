package com.dgcockpit.repository;

import com.dgcockpit.entity.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, String> {

    /** Toutes les étapes d'un type d'instruction, triées par ordre croissant */
    List<WorkflowStep> findByInstructionTypeIdOrderByStepOrderAsc(String instructionTypeId);

    /** Prochaine étape après une position donnée */
    Optional<WorkflowStep> findFirstByInstructionTypeIdAndStepOrderGreaterThanOrderByStepOrderAsc(
        String instructionTypeId, int currentOrder
    );

    /** Vérification de doublon : même type + même ordre */
    boolean existsByInstructionTypeIdAndStepOrder(String instructionTypeId, int stepOrder);
}
