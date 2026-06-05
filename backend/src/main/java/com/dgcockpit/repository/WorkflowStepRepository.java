package com.dgcockpit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dgcockpit.entity.WorkflowStep;

@Repository
public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, String> {
    List<WorkflowStep> findByWorkflowIdOrderByOrdre(String workflowId);
    Optional<WorkflowStep> findByWorkflowIdAndCode(String workflowId, String code);
}
