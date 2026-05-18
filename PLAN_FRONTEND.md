# Plan Frontend — DG Cockpit Douanes Djibouti
> Suivi des écrans et fonctionnalités frontend restant à implémenter.
> Légende : ✅ Terminé · 🔄 En cours · ⬜ À faire
> Mis à jour le 2026-05-19.

---

## Récapitulatif global

| # | Module | Écran(s) | Statut | % |
|---|--------|----------|--------|---|
| 1 | Authentification | Login + guards | ✅ | 100% |
| 2 | Dashboard | Tableau de bord | ✅ | 100% |
| 3 | Instructions | Flux two-pane + workflow | ✅ | 100% |
| 4 | Instructions | Vue Subordonné (mes instructions filtrées) | ⬜ | 0% |
| 5 | Parapheur | Liste À signer / Historique | ✅ | 100% |
| 6 | Parapheur | Viewer PDF + Signature/Rejet | ✅ | 100% |
| 7 | Parapheur | Post-signature : archiver / publier (S3) | ⬜ | 0% |
| 8 | Courrier Arrivé | Liste + Détail | ✅ | 100% |
| 9 | Courrier Arrivé | Décision DG : Classer / Créer Instruction | ⬜ | 0% |
| 10 | Courrier Arrivé | Saisie / Upload Secrétaire (S5) | ⬜ | 0% |
| 11 | Courrier Départ | Liste + Détail | ✅ | 100% |
| 12 | Agenda | Calendrier + Détail RDV | ✅ | 100% |
| 13 | Agenda | Demandes de RDV à valider (A3) | ⬜ | 0% |
| 14 | Agenda | Gestion Agenda Secrétaire (S4) | ⬜ | 0% |
| 15 | Bureau Secrétaire | Écran d'accueil + actions rapides (S1) | ⬜ | 0% |
| 16 | Bureau Secrétaire | Rédiger un document Courrier/Note (S2) | ⬜ | 0% |
| 17 | Fil Notes de Service | Feed chronologique publié (NS) | ⬜ | 0% |
| 18 | Signatures | Mes Signatures (blob URL fix) | 🔄 | 80% |
| 19 | Paramètres | Onglet Utilisateurs | ✅ | 100% |
| 20 | Paramètres | Déplacer Mes Signatures dans Paramètres | ⬜ | 0% |
| 21 | Nettoyage | Supprimer /editor, fusionner /pdf-documents → Parapheur | ⬜ | 0% |
| 22 | Design | Alignement design system global | ⬜ | 0% |

---

## Détail par étape

---

### Étape 4 — Vue Subordonné : Mes Instructions ⬜

**Objectif :** Un subordonné connecté ne voit que les instructions qui lui sont assignées, avec une distinction claire entre [Répondre] et [Réponse Définitive].

**Frontend — `/chat`**
- [ ] Filtrer la liste des threads côté API : `GET /api/instructions?assignee=<userId>` si rôle SUBORDONNE
- [ ] Afficher uniquement les instructions où l'utilisateur est assigné
- [ ] Bouton **[Répondre]** → message normal (thread reste ouvert)
- [ ] Bouton **[Réponse Définitive]** → message final avec choix du livrable (texte / fichier / document à signer)
- [ ] Indicateur visuel "En attente de votre réponse" sur les threads assignés non clôturés
- [ ] Masquer les boutons workflow (Soumettre / Valider) pour le rôle SUBORDONNE

**Backend requis**
- [ ] Endpoint `/api/instructions?assigneeId=<id>` ou filtrage par userId connecté

---

### Étape 5 — Bureau Secrétaire ⬜

**Objectif :** Espace de travail de la Secrétaire pour préparer les documents avant soumission au Parapheur.

#### Flux document
```
Upload PDF direct (navigateur) ──┐
                                  ├──→ POST /api/bureau/documents ──→ liste brouillons
Imprimante virtuelle C# ─────────┘                                        ↓
                                                          Placement zones signature/tampon
                                                                          ↓
                                                          Soumettre au Parapheur → DG signe
```

**Frontend — nouvelle route `/bureau`**
- [ ] Créer `BureauComponent` avec route `/bureau`
- [ ] Ajouter "Bureau" dans la sidebar (visible SECRETAIRE uniquement)
- [ ] Compteurs en haut : brouillons en attente / docs au parapheur / courriers à traiter / RDV du jour

**Frontend — Liste des brouillons**
- [ ] Tableau des `BureauDocument` de la Secrétaire connectée
- [ ] Colonnes : nom fichier, type, destinataire, date upload, statut (BROUILLON / SOUMIS)
- [ ] Bouton **"+ Uploader un PDF"** → sélecteur fichier → `POST /api/bureau/documents`
- [ ] Notification SSE quand un document arrive via l'imprimante virtuelle
- [ ] Actions par ligne : Ouvrir (placer zones) / Supprimer

**Frontend — Placement zones (réutilise `/pdf-viewer`)**
- [ ] Ouvrir le document dans une variante du viewer en mode "Placement Secrétaire"
- [ ] Bouton **"+ Zone Signature DG"** → crée un rectangle bleu pointillé déplaçable/redimensionnable
- [ ] Bouton **"+ Zone Tampon"** → crée un rectangle rouge pointillé déplaçable/redimensionnable
- [ ] Une seule zone Signature et une seule zone Tampon autorisées par document
- [ ] Les zones sont sauvegardées via `POST /api/bureau/documents/:id/zones`
- [ ] Bouton **"Soumettre au Parapheur"** (actif uniquement si au moins la zone Signature est posée)
  → appel `POST /api/bureau/documents/:id/soumettre`
  → crée le `PdfDocument` + soumet au circuit de validation

**Frontend — Saisie Courrier Arrivé (S5)**
- [ ] Formulaire dans `/bureau` : expéditeur, objet, date, scan PDF
- [ ] Upload fichier vers MinIO
- [ ] Création `CourrierArrive` en base via `POST /api/courriers-arrive`

**Backend requis**
- [ ] Entité `BureauDocument` : id, secretaireId, fileName, type, destinataire, bucket, objectKey, statut (BROUILLON/SOUMIS), signatureZone (JSON coords), tampZone (JSON coords), createdAt
- [ ] `POST /api/bureau/documents` — upload PDF → MinIO → persistance (utilisé par navigateur ET imprimante virtuelle)
- [ ] `GET /api/bureau/documents` — liste des brouillons de la Secrétaire connectée
- [ ] `DELETE /api/bureau/documents/:id`
- [ ] `POST /api/bureau/documents/:id/zones` — sauvegarder positions des zones
- [ ] `POST /api/bureau/documents/:id/soumettre` — crée `PdfDocument` + soumet au parapheur
- [ ] À la signature DG : remplacer `SIGNATURE_ZONE` et `STAMP_ZONE` par les vraies images avant finalisation

**Imprimante virtuelle C# (projet séparé)**
- [ ] Application .NET 8 installable sur poste Windows
- [ ] S'enregistre comme imprimante "Secrétariat Douanes" dans Windows
- [ ] Intercepte le job d'impression → convertit en PDF via GhostScript
- [ ] Pop-up : sélection type document + destinataire optionnel
- [ ] Upload vers `POST /api/bureau/documents` avec token Secrétaire
- [ ] Notification système "Document envoyé ✓"

---

### Étape 6 — Fil des Notes de Service ⬜

**Objectif :** Feed chronologique des notes de service publiées, visible par tous les rôles.

**Frontend — nouvelle route `/notes`**
- [ ] Créer `NotesDeServiceComponent` avec route `/notes`
- [ ] Ajouter "Notes de Service" dans la sidebar (tous les rôles)
- [ ] Liste des notes publiées (`parapheurStatut = PUBLIE`, `parapheurType = NOTE_SERVICE`)
- [ ] Card par note : titre, date de signature, aperçu du contenu, bouton "Lire"
- [ ] Lien "Lire" → ouvre le PDF dans `/pdf-viewer/:id` en mode lecture seule
- [ ] Badge "Nouveau" sur les notes publiées depuis la dernière visite
- [ ] Compteur dans la sidebar (notifications SSE sur nouvelle publication)

**Backend requis**
- [ ] Endpoint `GET /api/parapheur/notes-de-service` ✅ (déjà créé)
- [ ] SSE event `NOTE_PUBLIEE` à broadcaster à la signature d'une note

---

### Étape 7 — Workflow Courrier Arrivé : Décision DG ⬜

**Objectif :** Sur la page détail d'un courrier arrivé, le DG peut décider de classer ou de créer une instruction.

**Frontend — `/inbox/:id`**
- [ ] Bandeau de décision visible uniquement si `statut = EN_ATTENTE_DECISION` et rôle DG
- [ ] Bouton **"Classer"** → `PATCH /api/courriers-arrive/:id/classer` → statut passe à CLASSE
- [ ] Bouton **"Créer une Instruction"** → ouvre la modale Nouvelle Instruction pré-remplie (objet = objet du courrier, référence du courrier liée)
- [ ] Après création de l'instruction → lien vers le thread `/chat` créé
- [ ] Afficher la référence de l'instruction liée si déjà créée (`instructionId` sur le courrier)

**Backend requis**
- [ ] `PATCH /api/courriers-arrive/:id/classer`
- [ ] Champ `instructionId` sur `CourrierArrive` (FK optionnel)
- [ ] À la création d'une instruction depuis un courrier → setter `courrierArrive.instructionId`

---

### Étape 8 — Demandes de RDV et Gestion Agenda Secrétaire ⬜

**Objectif :** La Secrétaire valide, refuse ou replanifie les demandes de RDV entrantes.

**Frontend — onglet "Demandes" dans `/appointments`**
- [ ] Ajouter un onglet "Demandes en attente" dans `AppointmentListComponent`
- [ ] Liste des demandes : demandeur, motif, créneau souhaité, date de la demande
- [ ] Bouton **"Valider"** → confirme le RDV, envoie notification
- [ ] Bouton **"Refuser"** → champ commentaire, marque comme refusée
- [ ] Bouton **"Replanifier"** → sélecteur de date/heure alternatif
- [ ] Badge rouge sur l'item sidebar si demandes en attente > 0

**Frontend — Génération QR code**
- [ ] Bouton "Générer lien de demande" dans la page Agenda
- [ ] Affiche un QR code pointant vers un formulaire de demande de RDV

**Backend requis**
- [ ] Entité `DemandeRdv` (demandeur, motif, créneau, statut)
- [ ] `POST /api/demandes-rdv` (public ou avec token temporaire)
- [ ] `GET /api/demandes-rdv` (Secrétaire uniquement)
- [ ] `PATCH /api/demandes-rdv/:id/valider|refuser|replanifier`

---

### Étape 9 — Nettoyage et Design System ⬜

**Nettoyage routes**
- [ ] Supprimer la route `/editor` et le composant `EditorComponent`
- [ ] Supprimer la route `/pdf-documents` (fusionner l'upload dans le Bureau Secrétaire)
- [ ] Déplacer "Mes Signatures" de la sidebar vers un onglet dans Paramètres
- [ ] Retirer les items sidebar correspondants

**Design system global**
- [ ] Harmoniser les tailles de police (titre h1 = 24px, h2 = 18px, labels = 12px)
- [ ] Palette cohérente : primary blue-600, danger red-600, success green-600, warning amber-500
- [ ] Cards uniformes : `rounded-xl border border-gray-200 shadow-sm` partout
- [ ] Boutons primaires : `bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700`
- [ ] États vides uniformes : icône + message + bouton d'action
- [ ] Responsive tablette : breakpoint `md:` systématique, touch targets ≥ 44px
- [ ] Loader global cohérent (skeleton ou spinner Tailwind)

---

## Routes finales cibles

```
/login                     → LoginComponent
/dashboard                 → DashboardComponent          (tous)
/chat                      → ChatComponent               (DG, SUBORDONNE)
/bureau                    → BureauComponent             (SECRETAIRE)
/signature                 → SignatureComponent          (DG, SECRETAIRE)
/pdf-viewer/:id            → PdfViewerComponent          (DG, SECRETAIRE)
/notes                     → NotesDeServiceComponent     (tous)
/inbox                     → InboxListComponent          (DG, SECRETAIRE)
/inbox/:id                 → InboxDetailComponent        (DG, SECRETAIRE)
/outbox                    → OutboxListComponent         (DG, SECRETAIRE)
/outbox/:id                → OutboxDetailComponent       (DG, SECRETAIRE)
/appointments              → AppointmentListComponent    (DG, SECRETAIRE)
/appointments/:id          → AppointmentDetailComponent  (DG, SECRETAIRE)
/parametres                → ParametresComponent         (DG, ADMIN_IT)
```

---

*Document créé le 2026-05-19 — Référence de suivi frontend DG Cockpit.*
