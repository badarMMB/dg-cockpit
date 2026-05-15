package com.dgcockpit.config;

import com.dgcockpit.entity.*;
import com.dgcockpit.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDate;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedDemoData(
            CollaborateurRepository collaborateurRepo,
            InstructionRepository instructionRepo,
            InstructionMessageRepository messageRepo,
            CourrierArriveRepository courrierArriveRepo,
            CourrierDepartRepository courrierDepartRepo,
            RendezVousRepository rendezVousRepo) {

        return args -> {
            if (collaborateurRepo.count() > 0) return;

            // ── Collaborateurs ────────────────────────────────────────────
            collaborateurRepo.save(collab("Jean-Pierre Dubois", "jp.dubois@mag.gouv.fr", "Directeur Général (DG)"));
            collaborateurRepo.save(collab("Sophie Martin",     "s.martin@mag.gouv.fr",  "Secrétaire de Direction"));
            collaborateurRepo.save(collab("Marc Lemaire",      "m.lemaire@mag.gouv.fr", "Agent (Chef de Service)"));
            collaborateurRepo.save(collab("Alice Laurent",     "a.laurent@mag.gouv.fr", "Admin IT"));

            // ── Instructions ──────────────────────────────────────────────
            Instruction i1 = new Instruction();
            i1.setTitle("Rapport Trimestriel Q2");
            i1.setType("Demande de rapport");
            i1.setAgentDisplay("Sophie Martin");
            i1.setStatut(Instruction.StatutInstruction.EN_ATTENTE);
            instructionRepo.save(i1);

            InstructionMessage m1 = new InstructionMessage();
            m1.setInstruction(i1); m1.setSender("Sophie Martin"); m1.setSelf(false);
            m1.setText("Bonjour M. le Directeur, voici la première version du rapport Q2 comme demandé.");
            m1.setAttachmentName("Rapport_Q2_v1.pdf");
            messageRepo.save(m1);

            InstructionMessage m2 = new InstructionMessage();
            m2.setInstruction(i1); m2.setSender("DG"); m2.setSelf(true);
            m2.setText("Merci Sophie. Pouvez-vous rajouter l'annexe financière avant que je ne signe ?");
            messageRepo.save(m2);

            Instruction i2 = new Instruction();
            i2.setTitle("Contrat Prestataire IT"); i2.setType("Validation financière");
            i2.setAgentDisplay("Jean Dupont"); i2.setStatut(Instruction.StatutInstruction.CLOTURE);
            instructionRepo.save(i2);

            Instruction i3 = new Instruction();
            i3.setTitle("Préparation CA Septembre"); i3.setType("Organisation de réunion");
            i3.setAgentDisplay("Marc Lemaire"); i3.setStatut(Instruction.StatutInstruction.OUVERT);
            instructionRepo.save(i3);

            // ── Courriers Arrivés ─────────────────────────────────────────
            CourrierArrive ca1 = new CourrierArrive();
            ca1.setObjet("Rapport annuel 2025 — Préfecture de Paris");
            ca1.setExpediteur("Préfecture de Paris");
            ca1.setReference("PRF-2025-0421");
            ca1.setUrgent(true);
            ca1.setApercu("Suite à notre réunion du 10 mai, veuillez trouver ci-joint le rapport annuel...");
            ca1.setContenu("Madame, Monsieur le Directeur,\n\nSuite à notre réunion du 10 mai 2025, veuillez trouver ci-joint le rapport annuel consolidé pour l'exercice 2025.\n\nNous vous prions d'agréer l'expression de nos salutations distinguées.\n\nLe Préfet de Paris");
            ca1.setPieceJointe("Rapport_Annuel_2025.pdf");
            ca1.setStatut(CourrierArrive.Statut.NON_TRAITE);
            courrierArriveRepo.save(ca1);

            CourrierArrive ca2 = new CourrierArrive();
            ca2.setObjet("Demande de subvention — Association Culturelle Al-Nour");
            ca2.setExpediteur("Association Al-Nour");
            ca2.setReference("ASS-2025-0089");
            ca2.setApercu("Dans le cadre de notre programme annuel, nous sollicitons une subvention...");
            ca2.setContenu("Monsieur le Directeur Général,\n\nDans le cadre de notre programme annuel d'activités culturelles, nous avons l'honneur de solliciter auprès de votre direction une subvention de fonctionnement.\n\nVeuillez trouver en pièce jointe notre dossier complet.\n\nCordialement,\nLe Président");
            ca2.setPieceJointe("Dossier_Subvention_AlNour.pdf");
            ca2.setStatut(CourrierArrive.Statut.EN_COURS);
            ca2.setDateReception(LocalDate.now().minusDays(3));
            courrierArriveRepo.save(ca2);

            CourrierArrive ca3 = new CourrierArrive();
            ca3.setObjet("Convocation — Réunion interministérielle du 20 juin");
            ca3.setExpediteur("Ministère de l'Intérieur");
            ca3.setReference("MI-2025-1102");
            ca3.setApercu("Vous êtes convoqué à une réunion interministérielle le 20 juin 2025...");
            ca3.setContenu("Monsieur le Directeur Général,\n\nVous êtes convoqué à une réunion interministérielle qui se tiendra le 20 juin 2025 à 10h00 au Ministère de l'Intérieur, salle Beauvau.\n\nOrdre du jour : Coordination des politiques de sécurité pour le second semestre.\n\nCordialement");
            ca3.setStatut(CourrierArrive.Statut.NON_TRAITE);
            ca3.setDateReception(LocalDate.now().minusDays(1));
            courrierArriveRepo.save(ca3);

            // ── Courriers Départ ──────────────────────────────────────────
            CourrierDepart cd1 = new CourrierDepart();
            cd1.setObjet("Réponse à la demande de subvention — Association Al-Nour");
            cd1.setDestinataire("Association Al-Nour");
            cd1.setReference("DG-2025-0234");
            cd1.setApercu("Suite à l'examen de votre dossier de subvention, nous avons le plaisir de...");
            cd1.setContenu("Monsieur le Président,\n\nSuite à l'examen de votre dossier de subvention, nous avons le plaisir de vous informer que votre demande a été retenue pour un montant de 15 000 €.\n\nLe versement interviendra dans un délai de 30 jours.\n\nCordialement,\nLe Directeur Général");
            cd1.setPieceJointe("Reponse_Subvention_AlNour_Signe.pdf");
            cd1.setStatut(CourrierDepart.Statut.EXPEDIE);
            cd1.setDateEnvoi(LocalDate.now().minusDays(5));
            courrierDepartRepo.save(cd1);

            CourrierDepart cd2 = new CourrierDepart();
            cd2.setObjet("Note de service — Révision budgétaire Q3 2025");
            cd2.setDestinataire("Tous les chefs de service");
            cd2.setReference("DG-2025-0251");
            cd2.setApercu("Par la présente note de service, nous portons à votre connaissance les nouvelles...");
            cd2.setContenu("À l'attention de tous les chefs de service,\n\nPar la présente note de service, nous portons à votre connaissance les nouvelles directives budgétaires pour le troisième trimestre 2025.\n\nL'ensemble des dépenses exceptionnelles devra être reporté avant le 15 septembre.\n\nLe Directeur Général");
            cd2.setPieceJointe("Note_Service_Budget_Q3.pdf");
            cd2.setStatut(CourrierDepart.Statut.SIGNE);
            courrierDepartRepo.save(cd2);

            CourrierDepart cd3 = new CourrierDepart();
            cd3.setObjet("Invitation — Cérémonie de remise de distinctions honorifiques");
            cd3.setDestinataire("Préfecture de Paris");
            cd3.setReference("DG-2025-0267");
            cd3.setApercu("Nous avons l'honneur de vous convier à la cérémonie annuelle...");
            cd3.setContenu("Monsieur le Préfet,\n\nNous avons l'honneur de vous convier à la cérémonie annuelle de remise de distinctions honorifiques qui se tiendra le 5 juillet 2025 à 18h00.\n\nCordialement,\nLe Directeur Général");
            cd3.setStatut(CourrierDepart.Statut.BROUILLON);
            courrierDepartRepo.save(cd3);

            // ── Rendez-vous ───────────────────────────────────────────────
            RendezVous rdv1 = new RendezVous();
            rdv1.setTitre("Point hebdomadaire — Secrétaire de Direction");
            rdv1.setVisiteur("Sophie Martin");
            rdv1.setOrganisation("Direction Générale");
            rdv1.setHeure("09:00"); rdv1.setDuree("30 min");
            rdv1.setObjet("Revue des dossiers en cours et planning de la semaine.");
            rdv1.setStatut(RendezVous.Statut.TERMINE);
            rendezVousRepo.save(rdv1);

            RendezVous rdv2 = new RendezVous();
            rdv2.setTitre("Réunion budgétaire Q3");
            rdv2.setVisiteur("Marc Lemaire");
            rdv2.setOrganisation("Service Financier");
            rdv2.setContact("m.lemaire@mag.gouv.fr");
            rdv2.setHeure("10:30"); rdv2.setDuree("1h");
            rdv2.setObjet("Présentation des projections budgétaires pour le 3ème trimestre et arbitrages.");
            rdv2.setStatut(RendezVous.Statut.PLANIFIE);
            rendezVousRepo.save(rdv2);

            RendezVous rdv3 = new RendezVous();
            rdv3.setTitre("Délégation — Ambassade de France au Sénégal");
            rdv3.setVisiteur("M. Henri Leclerc");
            rdv3.setOrganisation("Ambassade de France — Dakar");
            rdv3.setContact("+221 33 889 38 00");
            rdv3.setHeure("14:00"); rdv3.setDuree("45 min");
            rdv3.setObjet("Discussion sur le renforcement de la coopération bilatérale dans le domaine administratif.");
            rdv3.setStatut(RendezVous.Statut.PLANIFIE);
            rendezVousRepo.save(rdv3);
        };
    }

    private Collaborateur collab(String name, String email, String role) {
        Collaborateur c = new Collaborateur();
        c.setName(name); c.setEmail(email); c.setRole(role);
        return c;
    }
}
