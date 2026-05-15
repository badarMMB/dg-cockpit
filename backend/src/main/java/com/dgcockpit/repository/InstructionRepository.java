package com.dgcockpit.repository;

import com.dgcockpit.entity.Instruction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InstructionRepository extends JpaRepository<Instruction, String> {
    List<Instruction> findAllByOrderByCreatedAtDesc();
    long countByStatutIn(List<Instruction.StatutInstruction> statuts);
    long countByStatut(Instruction.StatutInstruction statut);

    @Query("SELECT i FROM Instruction i WHERE LOWER(i.title) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(i.type) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(i.agentDisplay) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<Instruction> search(@Param("q") String q);
}
