package com.dgcockpit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dgcockpit.entity.WorkflowAction;

@Repository
public interface WorkflowActionRepository extends JpaRepository<WorkflowAction, String> {
    List<WorkflowAction> findByWorkflowInstanceIdOrderByCreatedAtDesc(String workflowInstanceId);
}
