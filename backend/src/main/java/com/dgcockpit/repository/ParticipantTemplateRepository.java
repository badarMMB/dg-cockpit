package com.dgcockpit.repository;

import com.dgcockpit.entity.ParticipantTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParticipantTemplateRepository extends JpaRepository<ParticipantTemplate, String> {

    List<ParticipantTemplate> findByInstructionTypeIdOrderByOrdreAsc(String instructionTypeId);

    void deleteByInstructionTypeId(String instructionTypeId);
}
