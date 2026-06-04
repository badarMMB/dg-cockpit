package com.dgcockpit.repository;

import com.dgcockpit.entity.InstructionMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InstructionMessageRepository extends JpaRepository<InstructionMessage, String> {
    List<InstructionMessage> findByInstructionIdOrderBySentAtAsc(String instructionId);
}
