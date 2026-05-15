package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "instruction_messages")
public class InstructionMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instruction_id", nullable = false)
    private Instruction instruction;

    private String sender;
    private boolean isSelf;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Enumerated(EnumType.STRING)
    private TypeMessage type = TypeMessage.NORMAL;

    private String attachmentName;

    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    @Enumerated(EnumType.STRING)
    private StatutMessage statut;

    private LocalDateTime sentAt = LocalDateTime.now();

    public enum TypeMessage { NORMAL, FINAL }
    public enum ActionType { SIGNATURE, INBOX, MEMO }
    public enum StatutMessage { PENDING, VALIDATED, REJECTED }

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instruction getInstruction() { return instruction; }
    public void setInstruction(Instruction instruction) { this.instruction = instruction; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public boolean isSelf() { return isSelf; }
    public void setSelf(boolean self) { isSelf = self; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public TypeMessage getType() { return type; }
    public void setType(TypeMessage type) { this.type = type; }
    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }
    public ActionType getActionType() { return actionType; }
    public void setActionType(ActionType actionType) { this.actionType = actionType; }
    public StatutMessage getStatut() { return statut; }
    public void setStatut(StatutMessage statut) { this.statut = statut; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}
