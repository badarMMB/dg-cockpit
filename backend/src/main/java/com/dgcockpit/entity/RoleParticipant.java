package com.dgcockpit.entity;

/**
 * Rôle d'un participant dans une instruction.
 *
 * Utilisé dans Assignee (instance) et ParticipantTemplate (configuration).
 * Permet de définir par Poste qui est rapporteur, participant, etc.
 */
public enum RoleParticipant {
    /** Responsable principal : rédige le livrable (compte rendu, rapport, etc.) */
    RAPPORTEUR,
    /** Contributeur actif : participe à la production du livrable */
    PARTICIPANT,
    /** Valide ou approuve le livrable produit */
    VALIDATEUR,
    /** Observateur : consultation uniquement, pas d'action requise */
    OBSERVATEUR,
    DECIDEUR,
    SIGNATAIRE,
    RESPONSABLE
}
