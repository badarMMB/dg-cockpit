package com.dgcockpit.repository;

import com.dgcockpit.entity.Instruction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InstructionRepository extends JpaRepository<Instruction, String> {
    List<Instruction> findAllByOrderByCreatedAtDesc();
    long countByStatutIn(List<Instruction.StatutInstruction> statuts);
}
