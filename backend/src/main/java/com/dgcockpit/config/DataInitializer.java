package com.dgcockpit.config;

import com.dgcockpit.entity.Collaborateur;
import com.dgcockpit.entity.Instruction;
import com.dgcockpit.entity.InstructionMessage;
import com.dgcockpit.repository.CollaborateurRepository;
import com.dgcockpit.repository.InstructionMessageRepository;
import com.dgcockpit.repository.InstructionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedDemoData(CollaborateurRepository collaborateurRepo,
                                   InstructionRepository instructionRepo,
                                   InstructionMessageRepository messageRepo) {
        return args -> {
            if (collaborateurRepo.count() > 0) return; // déjà initialisé

            // Collaborateurs
            collaborateurRepo.save(createCollaborateur("Jean-Pierre Dubois", "jp.dubois@mag.gouv.fr", "Directeur Général (DG)"));
            collaborateurRepo.save(createCollaborateur("Sophie Martin", "s.martin@mag.gouv.fr", "Secrétaire de Direction"));
            collaborateurRepo.save(createCollaborateur("Marc Lemaire", "m.lemaire@mag.gouv.fr", "Agent (Chef de Service)"));
            collaborateurRepo.save(createCollaborateur("Alice Laurent", "a.laurent@mag.gouv.fr", "Admin IT"));

            // Instruction démo 1
            Instruction i1 = new Instruction();
            i1.setTitle("Rapport Trimestriel Q2");
            i1.setType("Demande de rapport");
            i1.setAgentDisplay("Sophie Martin");
            i1.setStatut(Instruction.StatutInstruction.EN_ATTENTE);
            instructionRepo.save(i1);

            InstructionMessage m1 = new InstructionMessage();
            m1.setInstruction(i1);
            m1.setSender("Sophie Martin");
            m1.setSelf(false);
            m1.setText("Bonjour M. le Directeur, voici la première version du rapport Q2 comme demandé.");
            m1.setAttachmentName("Rapport_Q2_v1.pdf");
            messageRepo.save(m1);

            InstructionMessage m2 = new InstructionMessage();
            m2.setInstruction(i1);
            m2.setSender("DG");
            m2.setSelf(true);
            m2.setText("Merci Sophie. Pouvez-vous rajouter l'annexe financière avant que je ne signe ?");
            messageRepo.save(m2);

            // Instruction démo 2
            Instruction i2 = new Instruction();
            i2.setTitle("Contrat Prestataire IT");
            i2.setType("Validation financière");
            i2.setAgentDisplay("Jean Dupont");
            i2.setStatut(Instruction.StatutInstruction.CLOTURE);
            instructionRepo.save(i2);

            // Instruction démo 3
            Instruction i3 = new Instruction();
            i3.setTitle("Préparation CA Septembre");
            i3.setType("Organisation de réunion");
            i3.setAgentDisplay("Marc Lemaire");
            i3.setStatut(Instruction.StatutInstruction.OUVERT);
            instructionRepo.save(i3);
        };
    }

    private Collaborateur createCollaborateur(String name, String email, String role) {
        Collaborateur c = new Collaborateur();
        c.setName(name);
        c.setEmail(email);
        c.setRole(role);
        return c;
    }
}
