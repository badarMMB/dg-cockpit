package com.dgcockpit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dgcockpit.entity.WorkflowParticipant;

@Repository
public interface WorkflowParticipantRepository extends JpaRepository<WorkflowParticipant, String> {
    List<WorkflowParticipant> findByWorkflowStepId(String workflowStepId);
    void deleteByWorkflowStepId(String workflowStepId);
}
