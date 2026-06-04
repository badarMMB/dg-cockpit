package com.dgcockpit.service;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.BureauDocument;
import com.dgcockpit.entity.Instruction;
import com.dgcockpit.entity.PdfDocument;
import com.dgcockpit.entity.Poste;
import com.dgcockpit.repository.PdfDocumentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private final PdfDocumentRepository pdfRepo;

    public AuthorizationService(PdfDocumentRepository pdfRepo) {
        this.pdfRepo = pdfRepo;
    }

    public AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUser user)) {
            throw new AccessDeniedException("Utilisateur non authentifie");
        }
        return user;
    }

    public void requireInstructionAccess(Instruction instruction) {
        AppUser user = currentUser();
        boolean allowed = user.getId().equals(instruction.getCreatedById())
            || instruction.getAssignees().stream().anyMatch(a -> user.getId().equals(a.getUserId()));
        if (!allowed) {
            throw new AccessDeniedException("Acces instruction refuse");
        }
    }

    public void requireInstructionInitiator(Instruction instruction) {
        AppUser user = currentUser();
        if (!user.getId().equals(instruction.getCreatedById())) {
            throw new AccessDeniedException("Seul l'initiateur peut effectuer cette action");
        }
    }

    public void requireBureauOwner(BureauDocument document) {
        AppUser user = currentUser();
        if (!user.getId().equals(document.getProprietaireId())) {
            throw new AccessDeniedException("Acces document refuse");
        }
    }

    public void requireBureauSubmitAccess(BureauDocument document) {
        AppUser user = currentUser();
        if (!user.hasBureau() && document.getCircuitPdfDocumentId() == null) {
            throw new AccessDeniedException("Acces bureau refuse");
        }
    }

    // ── Droits WOPI ──────────────────────────────────────────────────────────

    /**
     * Contexte BUREAU : le propriétaire du document peut écrire, sauf si
     * le document est déjà signé ou livré.
     */
    public boolean canEditBureauDocument(AppUser user, BureauDocument doc) {
        if (doc.getStatut() == BureauDocument.Statut.SIGNE
                || doc.getStatut() == BureauDocument.Statut.LIVRE) {
            return false;
        }
        return doc.getProprietaireId().equals(user.getId())
            || user.hasHabilitation(Poste.Habilitation.CAN_VIEW_ALL);
    }

    /**
     * Contexte PARAPHEUR : seul le signataire courant du circuit peut éditer,
     * et uniquement si le document n'est pas encore signé.
     */
    public boolean canEditParapheurDocument(AppUser user, BureauDocument doc) {
        if (doc.getStatut() == BureauDocument.Statut.SIGNE) return false;
        if (doc.getPdfDocumentId() == null) return false;
        PdfDocument pdf = pdfRepo.findById(doc.getPdfDocumentId()).orElse(null);
        if (pdf == null) return false;
        return user.getId().equals(pdf.getCurrentSignataireUserId())
            && user.hasHabilitation(Poste.Habilitation.CAN_SIGN);
    }
}
