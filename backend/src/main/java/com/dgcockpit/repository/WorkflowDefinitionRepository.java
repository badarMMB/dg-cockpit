package com.dgcockpit.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dgcockpit.entity.WorkflowDefinition;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, String> {
    Optional<WorkflowDefinition> findByCode(String code);
}
