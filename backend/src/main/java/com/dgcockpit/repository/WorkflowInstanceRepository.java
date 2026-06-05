package com.dgcockpit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dgcockpit.entity.WorkflowInstance;
import com.dgcockpit.entity.WorkflowStatus;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, String> {
    List<WorkflowInstance> findByWorkflowDefinitionId(String workflowDefinitionId);
    List<WorkflowInstance> findByStatus(WorkflowStatus status);
    List<WorkflowInstance> findByCreatedById(String createdById);
}
