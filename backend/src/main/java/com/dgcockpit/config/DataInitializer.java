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
    CommandLineRunner seedUsers(
            AppUserRepository userRepo,
            com.dgcockpit.service.AuthService authService) {

        return args -> {
            if (userRepo.count() > 0) return;

            userRepo.save(user("dg",           "Directeur Général",           authService.hashPassword("dg1234"),          AppUser.Role.DG));
            userRepo.save(user("secretaire",   "Secrétaire de Direction",     authService.hashPassword("sec1234"),         AppUser.Role.SECRETAIRE));
            userRepo.save(user("agent.douane", "Mohamed Ali (Chef de Service)", authService.hashPassword("agent1234"),     AppUser.Role.SUBORDONNE));
            userRepo.save(user("admin",        "Administrateur IT",           authService.hashPassword("admin1234"),       AppUser.Role.ADMIN_IT));
        };
    }

    @Bean
    CommandLineRunner seedDomainTypes(
            InstructionTypeRepository instructionTypeRepo,
            ProofTypeRepository proofTypeRepo) {

        return args -> {
            if (instructionTypeRepo.count() > 0) return;

            // ── Types d'Instructions — Douanes Djibouti ────────────────────
            // Catégorie STRATÉGIQUE
            instructionTypeRepo.save(itype("AUDIT_RECETTES",         "Audit des recettes douanières",          InstructionType.Categorie.STRATEGIQUE,   InstructionType.Urgence.NORMAL,   InstructionType.TypeLivrable.DOCUMENT));
            instructionTypeRepo.save(itype("REFORME_ZLECAF",         "Réforme ZLECAF",                         InstructionType.Categorie.STRATEGIQUE,   InstructionType.Urgence.PLANIFIE, InstructionType.TypeLivrable.DOCUMENT));
            instructionTypeRepo.save(itype("AUDIT_COMPARATIF",       "Audit comparatif des services",          InstructionType.Categorie.STRATEGIQUE,   InstructionType.Urgence.PLANIFIE, InstructionType.TypeLivrable.DOCUMENT));

            // Catégorie OPÉRATIONNELLE
            instructionTypeRepo.save(itype("CIBLAGE_FRAUDE",         "Ciblage de fraude",                      InstructionType.Categorie.OPERATIONNELLE, InstructionType.Urgence.URGENT,   InstructionType.TypeLivrable.PREUVE));
            instructionTypeRepo.save(itype("BLOCAGE_CONTENEUR",      "Blocage de conteneur",                   InstructionType.Categorie.OPERATIONNELLE, InstructionType.Urgence.URGENT,   InstructionType.TypeLivrable.PREUVE));
            instructionTypeRepo.save(itype("ENQUETE_TERRAIN",        "Enquête terrain",                        InstructionType.Categorie.OPERATIONNELLE, InstructionType.Urgence.URGENT,   InstructionType.TypeLivrable.PREUVE));
            instructionTypeRepo.save(itype("SURVEILLANCE_RENFORCEE", "Renforcement surveillance",              InstructionType.Categorie.OPERATIONNELLE, InstructionType.Urgence.NORMAL,   InstructionType.TypeLivrable.PREUVE));
            instructionTypeRepo.save(itype("SUSPENSION_SYDONIA",     "Suspension accès SYDONIA",               InstructionType.Categorie.OPERATIONNELLE, InstructionType.Urgence.URGENT,   InstructionType.TypeLivrable.CONFIRMATION));

            // Catégorie MANAGÉRIALE
            instructionTypeRepo.save(itype("CONVOCATION_AGENT",      "Convocation d'agent",                    InstructionType.Categorie.MANAGERIALE,   InstructionType.Urgence.NORMAL,   InstructionType.TypeLivrable.CONFIRMATION));
            instructionTypeRepo.save(itype("ROULEMENT_EQUIPES",      "Roulement des équipes",                  InstructionType.Categorie.MANAGERIALE,   InstructionType.Urgence.PLANIFIE, InstructionType.TypeLivrable.DOCUMENT));
            instructionTypeRepo.save(itype("MUTATION_AGENT",         "Mutation d'agent",                       InstructionType.Categorie.MANAGERIALE,   InstructionType.Urgence.NORMAL,   InstructionType.TypeLivrable.DOCUMENT));
            instructionTypeRepo.save(itype("RAPPORT_ACTIVITE",       "Rapport d'activité",                     InstructionType.Categorie.MANAGERIALE,   InstructionType.Urgence.NORMAL,   InstructionType.TypeLivrable.DOCUMENT));

            // Catégorie JURIDIQUE
            instructionTypeRepo.save(itype("RECOURS_OPERATEUR",      "Recours opérateur",                      InstructionType.Categorie.JURIDIQUE,     InstructionType.Urgence.NORMAL,   InstructionType.TypeLivrable.DOCUMENT));
            instructionTypeRepo.save(itype("PROTOCOLE_TRANSAC",      "Protocole transactionnel",               InstructionType.Categorie.JURIDIQUE,     InstructionType.Urgence.NORMAL,   InstructionType.TypeLivrable.DOCUMENT));
            instructionTypeRepo.save(itype("TRANSMISSION_JUSTICE",   "Transmission dossier justice",           InstructionType.Categorie.JURIDIQUE,     InstructionType.Urgence.URGENT,   InstructionType.TypeLivrable.DOCUMENT));

            // ── Types de Preuves ───────────────────────────────────────────
            proofTypeRepo.save(ptype("Procès-verbal (PV)",         "PDF",              "Document officiel constatant une infraction ou une saisie"));
            proofTypeRepo.save(ptype("Photo terrain",              "JPG, PNG",         "Photographie prise sur le lieu de l'opération"));
            proofTypeRepo.save(ptype("Capture d'écran SYDONIA",    "JPG, PNG, PDF",    "Capture du système SYDONIA attestant d'une action ou d'un état"));
            proofTypeRepo.save(ptype("Vidéo de saisie",            "MP4, MOV",         "Enregistrement vidéo d'une saisie ou d'une opération terrain"));
            proofTypeRepo.save(ptype("Document scanné",            "PDF",              "Tout document physique numérisé comme preuve"));
            proofTypeRepo.save(ptype("Rapport terrain",            "PDF",              "Rapport rédigé suite à une mission ou opération de terrain"));
        };
    }

    @Bean
    CommandLineRunner seedDemoData(
            CollaborateurRepository collaborateurRepo,
            InstructionRepository instructionRepo,
            InstructionMessageRepository messageRepo,
            CourrierArriveRepository courrierArriveRepo,
            CourrierDepartRepository courrierDepartRepo,
            RendezVousRepository rendezVousRepo,
            TemplateCourrierDepartRepository templateRepo) {

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

            // ── Modèles de courrier départ ────────────────────────────────
            templateRepo.save(template("Note de service",
                "NOTE_SERVICE",
                "Note de service n° [REFERENCE] — [OBJET]",
                "À l'attention de [DESTINATAIRES],\n\nPar la présente note de service, nous portons à votre connaissance [OBJET].\n\nNous vous remercions de bien vouloir en prendre acte et d'assurer la diffusion auprès de vos équipes.\n\nFait à [LIEU], le [DATE]\n\nLe Directeur Général"));

            templateRepo.save(template("Circulaire",
                "CIRCULAIRE",
                "Circulaire n° [REFERENCE] relative à [OBJET]",
                "Mesdames, Messieurs les Chefs de service,\n\nLa présente circulaire a pour objet de préciser les modalités d'application de [OBJET].\n\nI. CONTEXTE\n[À compléter]\n\nII. DISPOSITIONS\n[À compléter]\n\nIII. ENTRÉE EN VIGUEUR\nLa présente circulaire prend effet à compter de sa date de signature.\n\nLe Directeur Général"));

            templateRepo.save(template("Lettre de réponse",
                "REPONSE",
                "Réponse à votre courrier du [DATE] — [REFERENCE]",
                "Monsieur / Madame,\n\nNous avons bien reçu votre courrier du [DATE] relatif à [OBJET] et nous vous en remercions.\n\nAprès examen attentif de votre demande, nous avons l'honneur de vous informer que [REPONSE].\n\nNous restons à votre disposition pour tout renseignement complémentaire.\n\nVeuillez agréer, Monsieur / Madame, l'expression de nos salutations distinguées.\n\nLe Directeur Général"));

            templateRepo.save(template("Lettre d'invitation",
                "INVITATION",
                "Invitation — [EVENEMENT] du [DATE]",
                "Monsieur / Madame,\n\nNous avons l'honneur de vous convier à [EVENEMENT] qui se tiendra le [DATE] à [HEURE], [LIEU].\n\nProgramme :\n[À compléter]\n\nMerci de bien vouloir confirmer votre participation avant le [DATE_LIMITE].\n\nVeuillez agréer, Monsieur / Madame, l'expression de nos salutations distinguées.\n\nLe Directeur Général"));
        };
    }

    private Collaborateur collab(String name, String email, String role) {
        Collaborateur c = new Collaborateur();
        c.setName(name); c.setEmail(email); c.setRole(role);
        return c;
    }

    private TemplateCourrierDepart template(String nom, String type, String objet, String contenu) {
        TemplateCourrierDepart t = new TemplateCourrierDepart();
        t.setNom(nom); t.setType(type); t.setObjet(objet); t.setContenu(contenu);
        return t;
    }

    private AppUser user(String username, String nomComplet, String passwordHash, AppUser.Role role) {
        AppUser u = new AppUser();
        u.setUsername(username); u.setNomComplet(nomComplet);
        u.setPasswordHash(passwordHash); u.setRole(role);
        return u;
    }

    private InstructionType itype(String code, String label, InstructionType.Categorie cat,
                                  InstructionType.Urgence urgence, InstructionType.TypeLivrable livrable) {
        InstructionType t = new InstructionType();
        t.setCode(code); t.setLabel(label); t.setCategorie(cat);
        t.setUrgenceDefaut(urgence); t.setLivrableAttendu(livrable);
        return t;
    }

    private ProofType ptype(String label, String formats, String description) {
        ProofType p = new ProofType();
        p.setLabel(label); p.setAcceptedFormats(formats); p.setDescription(description);
        return p;
    }
}
