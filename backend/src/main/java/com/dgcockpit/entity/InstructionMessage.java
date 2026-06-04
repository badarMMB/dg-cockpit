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

    private String attachmentName;
    private String audioUrl;

    /** Surlignages PDF posés lors d'un renvoi pour correction — JSON [{page,x,y,w,h},...] */
    @Column(columnDefinition = "TEXT")
    private String highlightsJson;

    /** Message généré automatiquement par le système (événement circuit) */
    @Column(columnDefinition = "boolean DEFAULT false")
    private boolean systemMessage = false;

    private LocalDateTime sentAt = LocalDateTime.now();

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
    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }
    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }
    public String getHighlightsJson() { return highlightsJson; }
    public void setHighlightsJson(String highlightsJson) { this.highlightsJson = highlightsJson; }
    public boolean isSystemMessage() { return systemMessage; }
    public void setSystemMessage(boolean systemMessage) { this.systemMessage = systemMessage; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}
