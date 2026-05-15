package com.dgcockpit.repository;

import com.dgcockpit.entity.ValidationStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ValidationStepRepository extends JpaRepository<ValidationStep, String> {
    List<ValidationStep> findByInstructionIdOrderByDateAsc(String instructionId);
}
