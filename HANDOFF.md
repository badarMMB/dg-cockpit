# DG-Cockpit — Handoff Technique

Document de transmission pour reprise du projet par une autre IA / un nouveau développeur.

**Date du handoff :** 2026-06-04 (session 11 — flux hybride Collabora/PDFBox + édition DG au parapheur)
**Branche courante :** `feat/type-documents-circuit-configure`
**Branche principale :** `master`
**Dernier commit :** `18737c3` — *feat: TypeDocument configurables + circuit multi-signataires + parapheur universel*

> ⚠️ **Working tree non commité massif** : les changements des sessions 7, 8, 9 et 10 sont dans le working tree. Committer avant merge vers `master`.

> ✅ **Collabora Online opérationnel** : conteneur `dg-cockpit-collabora` (port 9980) en cours d'exécution sur le réseau `dg-cockpit_default`. Backend Spring Boot sur port 8080 (Maven local). MinIO + PostgreSQL en Docker.

---

## 1. Vue d'ensemble

**DG-Cockpit** est une application full-stack de gestion des instructions, courriers, parapheur électronique et workflow documentaire pour un Directeur Général (DG), sa Secrétaire et ses Subordonnés.

### Stack technique

| Couche | Technologie |
|--------|-------------|
| Frontend | Angular 18.2 standalone components + Signals + Tailwind CSS |
| Backend | Spring Boot (Java 21) + Spring Data JPA + REST + SSE |
| Base de données | PostgreSQL 15 |
| Stockage objet | MinIO (compatible S3) |
| Auth | JWT (filtre Spring + intercepteur Angular) + SecurityConfig |
| Édition documentaire | **Collabora Online CODE 23.05** via protocole WOPI |
| Build/Deploy | Docker multi-stage (Node 20 → Maven JDK21 → JRE21 Alpine) |

### Modèle de déploiement

Image **standalone** unique : le JAR Spring Boot sert l'API REST **ET** les fichiers Angular compilés depuis `backend/src/main/resources/static/`. Le stack docker-compose démarre 4 conteneurs : `postgres`, `minio`, `app`, **`collabora`**.

---

## 2. Rôles et modèle métier

### Postes (nouveau modèle — actif)

Les rôles codés en dur ont été remplacés par l'entité **`Poste`** avec des **`Habilitation`** dynamiques.

| Code | Libelle | Habilitations |
|------|---------|---------------|
| `DG` | Directeur Général | Toutes (8/8) |
| `CS` | Chef de Service | CREATE, VALIDATE, REJECT, VIEW_ALL |
| `INS` | Inspecteur des Douanes | CREATE |
| `CTR` | Contrôleur Douanier | CREATE |
| `SEC` | Secrétaire de Direction | CREATE, CLOSE, VIEW_ALL |

**Affectation actuelle :** `dg` → DG | `secretaire` → SEC | `agent.douane` → CS | `ibrahim_said` → CTR | `ahmed.hassan` / `fatima.omar` → INS

### Module Instructions — modèle simplifié (session 7)

> **⚠️ Le moteur de workflow (WorkflowService/WorkflowController/WorkflowStep) a été entièrement supprimé.** Le module Instructions est désormais centré sur deux types d'instructions simples.

#### Deux natures d'instruction (configurable par type)

| Nature | Comportement |
|--------|-------------|
| **LIBRE** | Chat entre initiateur et assignés. Pas de document attendu. Clôture manuelle par l'initiateur uniquement. |
| **DOCUMENTAIRE** | Un document doit être produit. Les événements du circuit documentaire (upload, soumission, signature, renvoi, finalisation) sont reportés comme messages système dans le fil. Clôture automatique à la signature finale. |

#### Statuts simplifiés (3 états)

```
OUVERT   → Instruction créée, aucune réponse encore
EN_COURS → Au moins un message reçu (ou document créé/soumis)
CLOTURE  → Clôture manuelle (LIBRE, initiateur only) OU auto (DOCUMENTAIRE, signature finale)
```

#### Flux LIBRE

```
Initiateur crée instruction LIBRE (titre + assignés + urgence)
  └──▶ Fil de chat ouvert
         ├── Échange de messages texte/audio
         └── Initiateur clôture manuellement → statut CLOTURE
```

#### Flux DOCUMENTAIRE — entrée A (instruction d'abord)

```
Initiateur crée instruction DOCUMENTAIRE (type lié à TypeDocument X)
  └──▶ Fil ouvert avec bannière "📎 Document attendu : [TypeDocument.libelle]"
         └── Assigné clique "Produire le document"
               └──▶ Bureau pré-rempli avec typeDocumentId + sourceInstructionId
                      └── Circuit du document → messages système dans le fil
                             └── Signature finale → CLOTURE auto
```

#### Flux DOCUMENTAIRE — entrée B (upload d'abord)

```
Utilisateur uploade un document dont le TypeDocument a linkedInstructionTypeId
  └──▶ Section "Instruction" apparaît dans la modale d'upload :
         ├── "Nouvelle instruction" → instruction créée + BureauDocument.sourceInstructionId lié
         └── "Rattacher à une instruction existante" → picker instructions DOCUMENTAIRE ouvertes
```

#### Intégration Parapheur → Instructions

```
Parapheur [renvoi correction] → si bureau.sourceInstructionId → message système "↩️" dans instruction existante
                               → si pas de lien → crée instruction LIBRE "Correction" (rétro-compat)
Parapheur [signature finale]  → si bureau.sourceInstructionId → message "✅" + instruction CLOTURE auto
```

---

## 3. Architecture des fichiers

### Frontend Angular

```
src/app/
├── components/          # Composants partagés ponctuels
├── guards/              # AuthGuard, RoleGuard
├── interceptors/        # JWT interceptor
├── layout/              # Layout principal (sidebar + topbar)
├── pages/
│   ├── bureau/          # Bureau (upload documents + soumission parapheur)
│   │   ├── bureau.component.ts    # ★ Accepte sourceInstructionId query param
│   │   └── bureau.component.html  # ★ Section Instruction dans modal upload
│   ├── chat/            # ★ Flux des instructions (modèle LIBRE/DOCUMENTAIRE)
│   │   ├── chat.component.ts      # Signals simplifiés, produireDocument(), cloturerInstruction()
│   │   └── chat.component.html    # Bannière DOCUMENTAIRE, bouton "Produire", messages système
│   ├── parametres/      # ★ Configuration — Postes, Types d'instructions, Types de documents
│   │   └── parametres.component.ts  # LIBRE/DOCUMENTAIRE + linkedInstructionTypeId
│   ├── signature/       # Parapheur électronique
│   ├── pdf-viewer/      # Visualiseur PDF
│   └── ... (autres pages inchangées)
├── services/
│   ├── api.service.ts        # ★ cloturerInstruction(), uploadBureauDocument() avec sourceInstructionId
│   ├── auth.service.ts       # JWT + currentUser (avec posteId)
│   └── toast.service.ts
└── shared/
    ├── audio-recorder/
    ├── audit-timeline/
    ├── pdf-file-viewer/      # Visualiseur PDF page-par-page
    ├── search-bar/
    └── toast/
```

> **⚠️ `workflow-timeline/` a été supprimé** — composant inutile après la suppression du moteur workflow.

### Backend Spring Boot

```
backend/src/main/java/com/dgcockpit/
├── config/
├── controller/
│   ├── AuthController.java
│   ├── GlobalExceptionHandler.java     # AccesRefuseException→403, IllegalState→409
│   ├── InstructionController.java      # /cloturer endpoint + toThreadDto expose typeInstruction/createdById
│   ├── BureauController.java           # ★ sourceInstructionId sur upload + messages système circuit
│   ├── ParapheurController.java        # ★ messages système renvoi/signature + auto-clôture instruction
│   ├── ParametresController.java       # CRUD Poste + InstructionType (typeInstruction/typeDocumentAttenduId)
│   ├── FileController.java
│   ├── SignatureAssetController.java
│   └── DashboardController.java
├── entity/
│   ├── AppUser.java           # poste (Poste) + manager (auto-relation)
│   ├── Poste.java             # fonction + habilitations dynamiques
│   ├── Instruction.java       # statut OUVERT/EN_COURS/CLOTURE + createdById
│   ├── InstructionMessage.java # isSystemMessage (boolean — remplace TypeMessage enum)
│   ├── InstructionType.java   # ★ typeInstruction (LIBRE|DOCUMENTAIRE) + typeDocumentAttenduId
│   ├── Assignee.java          # simplifié : id, instructionId, userId, nomComplet
│   ├── BureauDocument.java    # ★ sourceInstructionId (remplace correctionInstructionId)
│   ├── PdfDocument.java
│   └── TypeDocument.java      # ★ linkedInstructionTypeId (FK vers InstructionType)
├── filter/                    # JwtAuthFilter (custom — PAS Spring Security)
├── repository/
│   ├── InstructionRepository.java
│   ├── InstructionMessageRepository.java
│   ├── PosteRepository.java
│   └── ... (autres inchangés)
├── service/
│   ├── ParametresService.java        # CRUD Poste + InstructionType (typeInstruction/typeDocumentAttenduId)
│   ├── DocumentFinalizationService   # ⚠️ NE PAS MODIFIER
│   └── ... (autres inchangés)
└── sse/                       # Server-Sent Events — NE PAS MODIFIER
```

> **⚠️ Fichiers supprimés :** `WorkflowService.java`, `WorkflowController.java`, `WorkflowStepRepository.java`, `WorkflowStep.java` — entièrement retirés.

### Modèle de données

```
Poste
  ├── id, code, libelle, actif
  └── habilitations : Set<Habilitation>
        (CAN_CREATE_INSTRUCTION, CAN_SIGN, CAN_VALIDATE,
         CAN_REJECT, CAN_CLOSE, CAN_MANAGE_USERS, CAN_MANAGE_TYPES, CAN_VIEW_ALL)

AppUser
  ├── poste → Poste (ManyToOne, EAGER)
  ├── manager → AppUser (auto-relation ManyToOne, LAZY)
  └── hasHabilitation(Poste.Habilitation) → boolean

InstructionType
  ├── code, label, categorie, urgenceDefaut, actif
  ├── typeInstruction : LIBRE | DOCUMENTAIRE  ← nouveau
  └── typeDocumentAttenduId : String (nullable, FK vers TypeDocument)  ← nouveau

TypeDocument
  ├── code, libelle, actif, ...
  └── linkedInstructionTypeId : String (nullable, FK vers InstructionType)  ← nouveau

Instruction
  ├── id, titre, instructionTypeId, urgence, echeance, confidentialite
  ├── statut : OUVERT | EN_COURS | CLOTURE  ← simplifié
  └── createdById : String  ← pour règle "initiateur only" sur clôture LIBRE

Assignee
  └── id, instructionId, userId, nomComplet  ← simplifié (RoleAssignee supprimé)

InstructionMessage
  ├── id, instructionId, senderId, senderNom, isSelf, texte, sentAt
  ├── audioUrl (nullable), attachmentName (nullable)
  └── isSystemMessage : boolean (défaut false)  ← remplace TypeMessage enum

BureauDocument
  ├── ... (champs existants)
  └── sourceInstructionId : String (nullable)  ← remplace correctionInstructionId
```

---

## 4. Endpoints API clés

### Authentification
- `POST /api/auth/login` — retourne `{ token, user }` ; `user.posteId` + `user.posteLibelle` inclus
- `GET /api/auth/me`

### Instructions
- `GET /api/instructions` — liste filtrée (sans `CAN_VIEW_ALL` → seulement les instructions assignées)
  - DTO inclut : `typeInstruction`, `typeDocumentAttendu`, `createdById`
- `POST /api/instructions` — création (body : `title`, `instructionTypeId`, `urgence`, `echeance`, `confidentialite`, `message`, `assignees`, `hasAudio`)
- `GET /api/instructions/{id}/messages`
- `POST /api/instructions/{id}/messages`
- `POST /api/instructions/{id}/cloturer` — clôture manuelle (vérifie `createdById == currentUser.id`)

### Bureau
- `GET /api/bureau/documents` — liste des documents du bureau courant
- `POST /api/bureau/documents` (multipart) — upload ; paramètre optionnel `sourceInstructionId`
  - Si `sourceInstructionId` fourni → message système "📄 Document créé…" injecté dans l'instruction
- `POST /api/bureau/documents/{id}/soumettre` — soumet au parapheur
  - Message système "📤 Soumis…" injecté dans l'instruction liée (via `sourceInstructionId`)
- `DELETE /api/bureau/documents/{id}`

### Parapheur
- `GET /api/parapheur` — liste documents en attente de signature
- `POST /api/parapheur/{id}/signer` — signe le document
  - Si signature finale ET `bureau.sourceInstructionId` → message "✅ Finalisé" + auto-clôture instruction
- `POST /api/parapheur/{id}/renvoyer` / `POST /api/parapheur/{id}/correction` — renvoi
  - Si `bureau.sourceInstructionId` → message "↩️ Renvoyé…" dans instruction existante
  - Sinon → crée instruction LIBRE "Correction" (rétro-compat)
- `GET /api/parapheur/{id}/render-page/{page}` — rendu PNG d'une page avec zones signature/cachet

### Paramètres (admin)
- `GET|POST|PUT|DELETE /api/parametres/instruction-types/{id}` — CRUD types d'instructions
  - DTO inclut `typeInstruction`, `typeDocumentAttenduId`
- `GET|POST|PUT|DELETE /api/parametres/type-documents/{id}` — CRUD types de documents
  - DTO inclut `linkedInstructionTypeId`
- `GET|POST|PUT|DELETE /api/parametres/postes/{id}` — CRUD postes
- `PATCH /api/parametres/postes/{id}/toggle`
- `GET|POST|PUT /api/parametres/users/{id}`

### Fichiers / SSE
- `POST /api/files/upload`, `GET /api/files/{name}`, `GET /api/files/{name}/info`, `GET /api/files/{name}/page/{n}`
- `GET /api/events` — SSE (`INSTRUCTION_CREATED`, `INSTRUCTION_UPDATED`, `FILE_UPLOADED`, `PARAPHEUR_UPDATED`)

---

## 5. Setup environnement

### Pré-requis
- Docker Desktop (Linux containers)
- Node.js 20+ (pour dev frontend hors Docker)
- JDK 21 + Maven 3.9+ (pour dev backend hors Docker)

### Build & run (standalone Docker)

```powershell
# Build l'image
docker build -f Dockerfile.standalone -t dg-cockpit:latest .

# Démarrage complet avec postgres + minio (recommandé)
docker-compose -f docker-compose.standalone.yml up -d
```

### ⚠️ Démarrage manuel (si postgres + minio sont déjà en route)

```powershell
# Récupérer le nom du réseau Docker
docker inspect dg-cockpit-postgres --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}'

# Démarrer avec le bon réseau
docker stop dg-cockpit-app; docker rm dg-cockpit-app
docker run -d --name dg-cockpit-app `
  --network dg-cockpit_default `
  -p 8080:8080 `
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://dg-cockpit-postgres:5432/dgcockpit `
  -e SPRING_DATASOURCE_USERNAME=ged_user `
  -e SPRING_DATASOURCE_PASSWORD=ged_password `
  -e MINIO_URL=http://dg-cockpit-minio:9000 `
  -e MINIO_PUBLIC_URL=http://localhost:9000 `
  -e MINIO_ACCESS_KEY=minioadmin `
  -e MINIO_SECRET_KEY=minioadmin `
  -e MINIO_BUCKET=dgcockpit `
  dg-cockpit:latest
```

Accès :
- App : http://localhost:8080
- MinIO console : http://localhost:9001 (login `minioadmin` / `minioadmin`)

### Variables d'environnement

| Variable | Valeur compose | Notes |
|----------|---------------|-------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://dg-cockpit-postgres:5432/dgcockpit` | hostname conteneur |
| `SPRING_DATASOURCE_USERNAME` | `ged_user` | |
| `SPRING_DATASOURCE_PASSWORD` | `ged_password` | |
| `MINIO_URL` | `http://dg-cockpit-minio:9000` | interne réseau Docker |
| `MINIO_PUBLIC_URL` | `http://localhost:9000` | doit être accessible depuis le navigateur |
| `MINIO_BUCKET` | `dgcockpit` | bucket principal |

Autre bucket : `ged-bureau-documents` (documents bureau).

### Dev frontend hors Docker

```powershell
npm install
npm start   # ng serve --port 4200 (proxy /api → localhost:8080)
```

### Dev backend hors Docker

```powershell
cd backend
mvn spring-boot:run
```

---

## 6. Polices et thème

**⚠️ NE PAS réintroduire Google Fonts** dans `styles.css`. Dans l'environnement Docker / proxy d'entreprise, l'import HTTP vers `fonts.googleapis.com` retourne du HTML qui fait planter le parser OTS.

Stack de polices (`tailwind.config.js`) :
```js
fontFamily: {
  'sans': ['Inter', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'sans-serif'],
}
```

---

## 7. Sessions — historique des modifications

### Session 2026-06-03 (session 8) — Audit du working tree

Aucune modification de code à ce passage : vérification de l'état réel par rapport à HANDOFF.md.

**Constats :**
- Branche `feat/type-documents-circuit-configure`, HEAD = `18737c3` (commit "TypeDocument configurables + circuit multi-signataires").
- La refonte LIBRE/DOCUMENTAIRE (session 7) existe **uniquement dans le working tree non commité** sur cette branche.
- `SecurityConfig.java` (Spring Security) a été ajouté en complément de `AuthFilter` — voir §8.
- Nouveaux fichiers backend non commités : `controller/PdfGenerationController.java`, `service/PdfGenerationService.java`, `service/DocxToHtmlConverter.java`, `service/AuthorizationService.java`, `repository/AssigneeRepository.java`, `config/SecurityConfig.java`, `resources/fonts/`, `resources/pdf-template.css`, `test/`.
- Nouveaux fichiers frontend non commités : `src/app/guards/permission.guard.ts`, `src/types/`, `proxy.conf.json`.
- Autres ajouts non commités : `DEV.md`, `docker-compose.dev.yml`, `migration_v3_instructions_simplification.sql` (la migration SQL v3 est désormais versionnée comme fichier).

**Action recommandée :** committer le working tree en plusieurs commits cohérents (1) refonte LIBRE/DOCUMENTAIRE, (2) SecurityConfig, (3) génération PDF/DOCX) avant tout merge.

---

### Session 2026-05-30 (session 7) — Simplification module Instructions LIBRE/DOCUMENTAIRE

**Objectif :** Supprimer le moteur de workflow parallèle (WorkflowService/WorkflowStep) et recentrer le module Instructions sur deux types simples : LIBRE (chat) et DOCUMENTAIRE (livrable document).

#### Backend

**Fichiers supprimés :**
- `WorkflowService.java`
- `WorkflowController.java`
- `WorkflowStepRepository.java`
- `entity/WorkflowStep.java`

**Fichiers modifiés :**

| Fichier | Modification |
|---------|-------------|
| `entity/Instruction.java` | Suppression statut legacy + globalStatus + currentStep + currentActor ; statut simplifié OUVERT/EN_COURS/CLOTURE ; ajout `createdById` |
| `entity/InstructionMessage.java` | Suppression TypeMessage enum + StatutMessage + actionType + workflowStep ; ajout `isSystemMessage` boolean |
| `entity/InstructionType.java` | Suppression workflowSteps + documentsAttendus + livrableAttendu ; ajout `typeInstruction` (LIBRE|DOCUMENTAIRE) + `typeDocumentAttenduId` |
| `entity/Assignee.java` | Suppression RoleAssignee |
| `entity/BureauDocument.java` | Ajout `sourceInstructionId` (remplace `correctionInstructionId`) |
| `entity/TypeDocument.java` | Ajout `linkedInstructionTypeId` |
| `controller/InstructionController.java` | Ajout endpoint `POST /{id}/cloturer` ; exposition `typeInstruction`, `typeDocumentAttenduId`, `createdById` dans `toThreadDto()` |
| `controller/BureauController.java` | Injection `instructionRepo` + `instructionMessageRepo` ; helper `ajouterMessageSysteme()` ; `sourceInstructionId` sur upload ; messages système sur soumettre |
| `controller/ParapheurController.java` | Helper `ajouterMessageSysteme()` ; messages système sur renvoi + signature finale ; auto-clôture instruction DOCUMENTAIRE |
| `service/ParametresService.java` | `applyInstructionTypeFields()` gère `typeInstruction` + `typeDocumentAttenduId` |
| `controller/ParametresController.java` | Exposition `typeInstruction` + `typeDocumentAttenduId` dans DTO InstructionType ; `linkedInstructionTypeId` dans DTO TypeDocument |

#### Frontend

**Fichiers supprimés :**
- `src/app/shared/workflow-timeline/workflow-timeline.component.ts`
- `src/app/shared/workflow-timeline/workflow-timeline.component.html`

**Fichiers modifiés :**

| Fichier | Modification |
|---------|-------------|
| `chat.component.ts` | Suppression signals workflow ; computed `isDocumentaire`, `peutCloturerManuellement` ; méthodes `produireDocument()`, `cloturerInstruction()` ; `availableAgents` → `{id, nomComplet}[]` |
| `chat.component.html` | Suppression bandeau workflow + timeline + ActionType footer + validation messages ; ajout bannière "📎 Document attendu" + bouton "Produire le document" ; messages système stylés différemment |
| `api.service.ts` | Suppression méthodes workflow ; ajout `cloturerInstruction()` ; `uploadBureauDocument()` avec `sourceInstructionId` param |
| `bureau.component.ts` | Ajout `ActivatedRoute` ; lecture `typeDocumentId` + `sourceInstructionId` en query params ; modal auto-ouvert si arrivée depuis Chat ; signals `uploadInstructionMode`, `instructionsOuvertes`, `creatingInstruction` |
| `bureau.component.html` | Section "Instruction" dans modal upload (visible si `linkedInstructionTypeId` non null) ; 3 options : Autonome / Nouvelle instruction / Rattacher à existante |
| `parametres.component.ts` | Suppression livrableAttendu + documentsAttendus + circuit modal + WorkflowStep CRUD ; ajout `typeInstruction` select + `typeDocumentAttenduId` picker ; TypeDocument : ajout `linkedInstructionTypeId` picker |

#### Migration SQL (exécutée en base le 2026-05-30)

```sql
ALTER TABLE instruction_types ADD COLUMN IF NOT EXISTS type_instruction VARCHAR(20) DEFAULT 'LIBRE';
ALTER TABLE instruction_types ADD COLUMN IF NOT EXISTS type_document_attendu_id VARCHAR(255);
ALTER TABLE bureau_documents ADD COLUMN IF NOT EXISTS source_instruction_id VARCHAR(255);
ALTER TABLE type_documents ADD COLUMN IF NOT EXISTS linked_instruction_type_id VARCHAR(255);
ALTER TABLE instruction_messages ADD COLUMN IF NOT EXISTS is_system_message BOOLEAN DEFAULT FALSE;
```

---

### Session 2026-05-26 (session 6) — UI workflow enrichie + données d'exemple + bug fix persistance

**Bug critique corrigé :** instructions non persistantes — `createInstruction()` n'initialisait pas `globalStatus` → NPE sur `toThreadDto()` → liste vide.

**Données d'exemple injectées en base :** 5 postes + affectation utilisateurs + 12 étapes de circuit sur 5 types.

**Améliorations UI chat :** bordures colorées par statut, panneau timeline droit, aperçu circuit dans modale de création, checkbox "Lancer immédiatement".

---

### Sessions 2026-05-25 (sessions 1→5) — Refonte moteur workflow (supersédé par session 7)

> Ces sessions ont introduit le moteur WorkflowService/WorkflowStep/WorkflowController qui a été **entièrement supprimé en session 7**. Voir git log pour l'historique complet si nécessaire.

---

### Session 2026-06-04 (session 10) — Gabarit .docx direct + aperçu PDF en modale + cache MinIO

#### Contexte

Remplacement du flux Quill/Mammoth dans Paramètres > Types de Documents par un upload `.docx` natif, avec prévisualisation PDF en modale et cache pré-converti pour un aperçu instantané.

#### Backend

| Fichier modifié | Changement |
|---|---|
| `entity/TypeDocument.java` | +`templatePdfPath` (chemin MinIO du PDF pré-converti) + getter/setter |
| `controller/TypeDocumentController.java` | +`CollaboraConvertService` injecté ; `upload-template` stocke `.docx` ET `.pdf` pré-converti ; `template-preview` sert le PDF depuis MinIO (fallback conversion-à-la-volée si `templatePdfPath` null) ; `appliquerBody()` + `toDto()` exposent `templatePdfPath` |
| `service/CollaboraConvertService.java` | **Bug fix critique** : `HttpEntity<byte[]>` → `ByteArrayResource` avec `getFilename()` — Spring n'incluait pas le filename dans le multipart, Collabora ne détectait pas le format `.docx` → "Not found" → 500 |

> **⚠️ Bug fix `CollaboraConvertService`** : ce fix corrige aussi `BureauController.soumettre()` et `ParapheurController.signer()` qui appelaient le même `docxToPdf()`. Le flux `.docx` complet (soumettre + signer) n'était jamais fonctionnel avant ce correctif.

#### Frontend

| Fichier modifié | Changement |
|---|---|
| `services/api.service.ts` | `uploadTemplate()` retourne `{ docxPath, pdfPath, fileName }` ; +`getTemplatePreviewBlob(id)` |
| `pages/parametres/parametres.component.ts` | Interface `TypeDocParam` +`templatePdfPath` ; `onTemplateUpload()` stocke `pdfPath` ; `previewTemplate()` ouvre la modale immédiatement avec spinner ; `closeTemplatePreview()` libère l'ObjectURL ; modale `@if (showTemplatePreview())` avec `<iframe [src]>` + spinner pendant chargement |

#### Flux gabarit .docx (session 10)

```
Admin charge un .docx dans Paramètres → Types de Documents
  │
  ▼ POST /api/type-documents/upload-template
  MinIO : templates/{uuid}.docx  +  templates/{uuid}.pdf (pré-converti via Collabora)
  ↳ TypeDocument.templateDocxPath + templatePdfPath persistés
  │
  ▼ Bouton "👁 Aperçu" (visible si doc sauvegardé)
  Modale s'ouvre IMMÉDIATEMENT avec spinner
  GET /api/type-documents/{id}/template-preview
  → MinIO download templatePdfPath (~ms)   [ou fallback conversion si path null]
  → <iframe> affiche le PDF
```

#### Modèle de données — nouvelle colonne

```sql
-- Ajoutée automatiquement par ddl-auto=update au redémarrage
ALTER TABLE type_documents ADD COLUMN IF NOT EXISTS template_pdf_path VARCHAR(255);
```

---

### Session 2026-06-03 (session 9) — Intégration Collabora Online WOPI (Lots 1-2-3)

**Abandon de la conversion `.docx → HTML` (Mammoth/JSoup) et de ngx-quill.** Le format master est désormais le `.docx` natif stocké dans MinIO, édité via Collabora Online embarqué dans une `<iframe>`.

#### Lot 1 — Infrastructure WOPI socle

| Fichier créé/modifié | Rôle |
|---|---|
| `entity/WopiToken.java` | Token court (TTL 1h) lié à (user, bureauDoc, canWrite) |
| `repository/WopiTokenRepository.java` | `findByToken`, `deleteByBureauDocumentId`, purge |
| `service/WopiTokenService.java` | `issue()`, `validate()`, `invalidateForDocument()`, purge `@Scheduled` |
| `controller/WopiController.java` | CheckFileInfo / GetFile / PutFile — 3 endpoints WOPI standard |
| `filter/AuthFilter.java` | `/api/wopi/**` exempté du JWT |
| `config/SecurityConfig.java` | `permitAll()` sur `/api/wopi/**` |
| `service/MinioService.java` | `sizeOf(bucket, objectKey)` requis par CheckFileInfo |
| `docker-compose.standalone.yml` | Service `collabora/code:23.05.10.1.1` + variables app |
| `application.properties` | Clés `collabora.*` + fix `wopi.host=host.docker.internal:8080` |
| `migration_v4_collabora_wopi.sql` | Table `wopi_tokens` + index |
| `DgCockpitApplication.java` | `@EnableScheduling` pour la purge des tokens |

#### Lot 2 — Sessions & iframe Angular

| Fichier créé/modifié | Rôle |
|---|---|
| `service/AuthorizationService.java` | +`canEditBureauDocument()`, +`canEditParapheurDocument()` |
| `controller/BureauController.java` | +`GET /documents/{id}/wopi-session`, +`POST /documents/from-template`, upload `.docx` accepté (skip PDFBox page count) |
| `controller/ParapheurController.java` | +`GET /{id}/wopi-session` |
| `shared/collabora-editor/` | `CollaboraEditorComponent` : `[src]` SafeResourceUrl, postMessage Collabora, `injectSignature()`, `forceSave()` |
| `services/api.service.ts` | +`openBureauWopiSession()`, +`openParapheurWopiSession()`, +`createFromTemplate()` |
| `pages/bureau/bureau.component.*` | Overlay plein écran Collabora, bouton "✏️ Éditer/Corriger dans Collabora", bouton "📋 Créer depuis modèle", `peutSoumettre()` bypass pour `.docx` |
| `pages/signature/signature.component.*` | Overlay Collabora Parapheur, bouton "✅ Signer le document" |
| `entity/TypeDocument.java` | Champ `templateDocxPath` déjà présent — désormais utilisé |

#### Lot 3 — Pipeline de signature finale

| Fichier créé/modifié | Rôle |
|---|---|
| `service/CollaboraConvertService.java` | `POST /cool/convert-to/pdf` → retourne `byte[]` PDF |
| `service/DocxSignatureService.java` | Apache POI : remplace `[SIGN_DG]` par l'image de signature dans le `.docx` |
| `controller/BureauController.java` — `soumettre()` | Si `.docx` : convertit en PDF via Collabora avant de créer le `PdfDocument` |
| `controller/ParapheurController.java` — `signer()` | 1. Injecte `[SIGN_DG]` (DocxSignatureService) 2. Convertit `.docx → PDF` si besoin (fallback) 3. `DocumentFinalizationService.finalize()` 4. Invalide WopiToken |
| `services/api.service.ts` | +`getMySignatureAsset()` (récupère base64 image signature) |
| `pages/signature/signature.component.ts` | `signerDocumentCollabora()` : inject signature → attente `documentSaved` → `signerDocument()` |

#### Flux `.docx` complet (session 9)

```
TypeDocument.templateDocxPath (MinIO dgcockpit)
  │
  ▼ [Bureau → Créer depuis modèle]
BureauDocument .docx (MinIO ged-bureau-documents)
  │
  ▼ [Éditeur Collabora — iframe]
  Secrétaire rédige + tape [SIGN_DG] à l'emplacement de signature
  │
  ▼ [Soumettre]
  CollaboraConvertService.docxToPdf() → PDF temporaire (ged-documents)
  PdfDocument créé pointant sur le PDF → Parapheur
  │
  ▼ [Parapheur DG — iframe Collabora]
  DG révise le .docx (édition live), puis clique "✅ Signer"
  │
  ▼ [signer()]
  DocxSignatureService : [SIGN_DG] → image de signature (4×2 cm)
  CollaboraConvertService.docxToPdf() → PDF signé
  DocumentFinalizationService.finalize() → PDFBox scellement
  WopiToken invalidé → document verrouillé
  BureauDocument.statut = SIGNE
```

#### Migration SQL v4

```sql
CREATE TABLE wopi_tokens (token VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64),
  bureau_document_id VARCHAR(64), can_write BOOLEAN, expires_at TIMESTAMPTZ, created_at TIMESTAMPTZ);
```

Appliquée automatiquement par `ddl-auto=update` au démarrage.

---

### Session 2026-06-04 (session 11) — Flux hybride Collabora/PDFBox + édition DG au parapheur

#### Contexte

Le mécanisme `[SIGN_DG]` (marqueur textuel remplacé par l'image via `DocxSignatureService`) ne fonctionnait pas : l'image de signature n'apparaissait jamais dans le PDF final. Décision : **abandonner ce mécanisme** et revenir à l'**ancien système PDFBox** (zones-pixel → `PageAnnotation` → `DocumentFinalizationService.finalize()`), qui fonctionnait parfaitement, tout en **conservant Collabora pour l'édition** du `.docx`.

#### Principe du flux hybride

```
1. Édition / modification → Collabora sur le .docx
   └─▶ À chaque enregistrement (WopiController.putFile), le PDF est régénéré
       automatiquement depuis le .docx via CollaboraConvertService

2. Placement des zones de signature → bureau-placement sur le PDF régénéré
   (composant bureau-placement inchangé — dessine sur rendu PDFBox)

3. Signature → DocumentFinalizationService.finalize() brûle les images PDFBox
   (ancien système restauré — les zones PDF et les images correspondent exactement)
```

#### Backend

| Fichier modifié | Changement |
|---|---|
| `entity/BureauDocument.java` | +`signaturePdfKey` : clé MinIO du PDF régénéré depuis le .docx à chaque save Collabora (colonne `signature_pdf_key` ajoutée par `ddl-auto=update`) |
| `controller/WopiController.java` | **Cœur du flux** : `putFile()` déclenche `collaboraConvert.docxToPdf()` après chaque enregistrement, réécrit la même clé MinIO (idempotent), reset des zones si la pagination change ; si le doc est déjà soumis au parapheur (`EN_ATTENTE_SIGNATURE`), re-synchronise le `PdfDocument` (bucket/objectKey/pageCount + `PageAnnotation` recréées) pour que les corrections DG apparaissent dans le PDF final. Injecte : `CollaboraConvertService`, `DocumentFinalizationService`, `PdfDocumentRepository`, `PageAnnotationRepository`, `ObjectMapper` |
| `controller/BureauController.java` — `renderPage()` | Rend le `signaturePdfKey` pour les `.docx` (PDFBox peut lire le PDF, pas le `.docx`) |
| `controller/BureauController.java` — `soumettre()` | Réutilise `signaturePdfKey` (PDF exact sur lequel les zones ont été posées, pas de re-conversion) ; zones désormais requises aussi pour les `.docx` |
| `controller/BureauController.java` — `toDto()` | +`hasSignaturePdf` (vrai si `signaturePdfKey != null`) |
| `controller/ParapheurController.java` — `signer()` | Suppression du bloc `DocxSignatureService.injectSignature()` (`[SIGN_DG]`) — plus utilisé. Retour au brûlage PDFBox pur via `finalizer.finalize()`. Filet de sécurité : conversion `.docx → PDF` si le `PdfDocument` pointe encore vers un `.docx` (données antérieures) |
| `controller/ParapheurController.java` | +`GET /{pdfDocId}/source-docx` : retourne `{ bureauDocumentId, isDocx }` pour que le pdf-viewer ouvre Collabora |

#### Frontend

| Fichier modifié | Changement |
|---|---|
| `services/api.service.ts` | +`getSourceDocx(pdfDocId)` |
| `pages/bureau/bureau.component.ts` | Interface `BureauDoc` +`hasSignaturePdf` ; suppression `return true` pour `.docx` dans `peutSoumettre()` (exige les zones) ; +`peutPlacerZones()` (désactivé tant que `signaturePdfKey` null pour un `.docx`) |
| `pages/bureau/bureau.component.html` | Suppression bulle `[SIGN_DG]` ; bouton « 📐 Placer zones » unifié PDF + `.docx` avec tooltip « Ouvrez d'abord dans Collabora » si pas encore de PDF ; suppression conditions `!isDocx(doc)` sur tooltips soumission |
| `pages/pdf-viewer/pdf-viewer.component.ts` | Import + usage `CollaboraEditorComponent` ; signal `bureauDocumentId` chargé via `getSourceDocx()` ; overlay plein écran Collabora (`editing`) ; bouton « ✏️ Éditer dans Collabora » dans la toolbar parapheur ; `fermerEdition()` recharge le document (PDF re-synchronisé) |

#### Flux `.docx` complet (session 11 — hybride)

```
Bureau → "Éditer dans Collabora" → modifier le .docx
  │
  ▼ Enregistrement Collabora (WopiController.putFile)
  CollaboraConvertService.docxToPdf() → PDF stocké sous signaturePdfKey
  BureauDocument.pageCount mis à jour
  (si déjà soumis → PdfDocument + PageAnnotation re-synchronisés)
  │
  ▼ "📐 Placer zones" (actif si signaturePdfKey présent)
  bureau-placement rend le PDF (renderPageFromStorage(signaturePdfKey))
  Secrétaire dessine les zones → signatureZonesJson / stampZonesJson
  │
  ▼ "📨 Soumettre"
  PdfDocument créé pointant sur signaturePdfKey (PDF exact des zones)
  PageAnnotation créées depuis les zones
  │
  ▼ Parapheur DG — pdf-viewer
  "✏️ Éditer dans Collabora" → corrections → fermer → PDF rechargé
  "✍️ Signer" → resolveZones() → finalizer.finalize() → PDFBox brûle les images
  ✅ Signature/tampon exactement dans les zones dessinées
```

#### Modèle de données — nouvelle colonne

```sql
-- Ajoutée automatiquement par ddl-auto=update au redémarrage backend
ALTER TABLE bureau_documents ADD COLUMN IF NOT EXISTS signature_pdf_key VARCHAR(255);
```

---

### Sessions antérieures (historique git)

- `9c82a38` — Notes de Service, routing SUBORDONNE, déploiement Docker standalone
- `6c6acdb` — Endpoint rendu PDF avec zones signature/cachet
- `cdbfc69` — Toasts + améliorations chat
- `a5b0c2a` — Système de classeurs (folder management)

---

## 8. Points d'attention / pièges connus

| Sujet | Détail |
|-------|--------|
| **Spring Security + AuthFilter custom** | ⚠️ Une `SecurityConfig` Spring Security a été ajoutée (working tree non commité) en complément du filtre custom `AuthFilter.java`. Le filtre custom reste la source de vérité JWT ; `SecurityConfig` désactive csrf/formLogin/httpBasic/logout et délègue à `AuthFilter`. `AccesRefuseException` → 403 via `GlobalExceptionHandler`. |
| **WorkflowService supprimé** | Ne pas tenter de le réintroduire. Le circuit documentaire passe par Bureau → Parapheur, les événements sont injectés comme messages système dans l'instruction liée. |
| **Clôture LIBRE** | Seul l'initiateur (`createdById`) peut clôturer manuellement. Le backend vérifie `createdById == currentUser.id` dans `/cloturer`. |
| **Clôture DOCUMENTAIRE** | Auto-clôture par `ParapheurController` lors de la signature finale si `bureau.sourceInstructionId != null`. Ne pas clôturer manuellement côté frontend pour ce type. |
| **isSystemMessage** | Les messages système sont stylés différemment dans `chat.component.html` (fond gris, italique, pas de bulle de chat normale). Ne pas les confondre avec les messages utilisateur. |
| **sourceInstructionId** | Champ unique qui fait le lien BureauDocument ↔ Instruction. Remplace l'ancien `correctionInstructionId`. Si null → comportement legacy (instruction LIBRE "Correction" créée). |
| **linkedInstructionTypeId** | Sur TypeDocument : définit si les documents de ce type déclenchent la section "Instruction" dans la modal upload du bureau. Si null → pas de section (document autonome). |
| **Docker sans réseau** | `docker run` sans `--network` → app cherche postgres sur `localhost:5432` → échec. Voir §5. |
| **iframe + JWT** | Ne jamais afficher PDF via `<iframe src="/api/files/...">` → 401. Utiliser `PdfFileViewerComponent`. |
| **Google Fonts** | Ne pas réintroduire (cf. §6). |
| **DocumentFinalizationService + SseService** | Ne jamais modifier ces deux services. Signatures brûlées côté serveur via PDFBox. |
| **Présignature MinIO** | `MINIO_PUBLIC_URL` doit être accessible depuis le navigateur client, pas le hostname interne Docker. |
| **Migration Hibernate** | `ddl-auto=update` ajoute les nouvelles colonnes mais ne supprime pas les anciennes. Certains champs legacy (`Instruction.statut` old values) peuvent encore exister en base. |
| **WOPI — `/api/wopi/**` public** | Ces endpoints sont exemptés du filtre JWT (`AuthFilter` + `SecurityConfig`). La sécurité est portée par `WopiToken` validé par `WopiTokenService`. Ne pas re-ajouter du JWT sur ces routes. |
| **WOPI — `host.docker.internal`** | En dev local (Spring Boot Maven + Collabora Docker), `collabora.wopi.host` doit être `http://host.docker.internal:8080` et non `localhost:8080`. Collabora (conteneur Docker) résout `localhost` comme lui-même, pas le host Windows. |
| **Collabora — `aliasgroup1`** | Le domaine WOPI source doit être whitelisté dans `aliasgroup1` sinon Collabora refuse de charger le document ("Host not allowed"). Inclure `http://host.docker.internal:8080` en dev. |
| **Collabora — iframe `[src]`** | L'iframe Collabora utilise `[src]="SafeResourceUrl"` avec l'`access_token` en query param. Ne pas revenir à un `<form method="post">` : Angular sanitise `[action]` avec `SecurityContext.URL` (pas `RESOURCE_URL`) → URL vidée → iframe vide. |
| **WopiToken vs JWT** | Les deux coexistent mais ne se mélangent pas : JWT en `Authorization: Bearer` header (navigateur → Spring), WopiToken en `?access_token=` query param (Collabora → Spring WOPI endpoints server-to-server). |
| **Conversion .docx → PDF** | Toute soumission au Parapheur d'un `.docx` passe par `CollaboraConvertService.docxToPdf()` avant création du `PdfDocument`. `DocumentFinalizationService` (PDFBox) n'accepte que des PDF. Ne jamais faire pointer un `PdfDocument` vers un `.docx`. |
| **CollaboraConvertService — filename obligatoire** | `docxToPdf(bytes, filename)` utilise `ByteArrayResource` avec `getFilename()`. **Ne jamais revenir à `HttpEntity<byte[]>`** : sans filename dans le Content-Disposition multipart, Collabora ne détecte pas le format `.docx` et retourne "Not found" (→ RuntimeException → 500). |
| **templatePdfPath** | Sur `TypeDocument` : PDF pré-converti stocké dans MinIO bucket `dgcockpit` sous `templates/{uuid}.pdf`. Généré automatiquement à l'upload via `upload-template`. Si null (anciens enregistrements), `template-preview` fait la conversion à la volée. |
| **Marqueur `[SIGN_DG]` — ABANDONNÉ** | ⚠️ `DocxSignatureService.injectSignature()` et le marqueur `[SIGN_DG]` ne sont plus utilisés. Le bloc correspondant dans `ParapheurController.signer()` a été supprimé. La signature passe uniquement par les zones-pixel PDFBox. Ne pas le réintroduire. |
| **`signaturePdfKey` — même clé réutilisée** | `WopiController.putFile()` réécrit toujours la même clé MinIO à chaque enregistrement Collabora (idempotent). MinIO garde l'historique via les clés archivées `_v_{timestamp}` mais le PDF courant est toujours accessible via la même clé. |
| **Re-synchronisation PdfDocument** | Si le DG édite le `.docx` dans Collabora au parapheur, le `PdfDocument` et les `PageAnnotation` sont automatiquement re-synchronisés dans `WopiController.putFile()`. Le pdf-viewer recharge après `fermerEdition()` pour afficher le PDF à jour. |
| **`.docx` sans enregistrement Collabora** | Un `.docx` uploadé sans jamais être ouvert dans Collabora n'a pas de `signaturePdfKey`. Le bouton « 📐 Placer zones » reste désactivé jusqu'au premier enregistrement. |
| **SSL Collabora en prod** | `--o:ssl.enable=false` est dev uniquement. En production : reverse proxy HTTPS (nginx + Let's Encrypt) devant Collabora port 9980. Collabora refuse le mode non-SSL sur des domaines publics. |

---

## 9. Mémoire IA / Conventions

Le dossier `C:\Users\User\.claude\projects\c--Users-User-Desktop-test-angular18-cyclos-nouveauTest-dg-cockpit\memory\` contient les mémoires persistantes :

- `MEMORY.md` — index
- `feedback_signature_zones.md` — règle métier sur les zones de signature
- `feedback_collabora_resttemplate.md` — fix CollaboraConvertService (ByteArrayResource + filename)
- `project_template_docx_flow.md` — flux upload gabarit .docx + cache PDF

Toute IA reprenant le projet devrait :
1. Lire `MEMORY.md` au démarrage
2. Mettre à jour les mémoires `feedback`/`project` au fil des sessions
3. Respecter le style **français** dans les commits, commentaires UI, labels
4. **Lire ce HANDOFF.md en priorité** — il contient l'état exact de la migration en cours

---

## 10. Backlog — prochaines étapes

### ✅ Terminé

- [x] Entités `Poste`, CRUD postes/habilitations
- [x] Suppression moteur workflow (WorkflowService/WorkflowStep)
- [x] Simplification module Instructions (LIBRE/DOCUMENTAIRE, 3 statuts)
- [x] Intégration Bureau ↔ Instructions via `sourceInstructionId`
- [x] Messages système dans le fil d'instruction (événements circuit documentaire)
- [x] Auto-clôture instruction DOCUMENTAIRE à la signature finale
- [x] Renvoi parapheur : message système dans instruction existante (pas de nouvelle instruction)
- [x] Frontend : bannière DOCUMENTAIRE + bouton "Produire le document" + modal upload enrichie
- [x] Paramètres : configuration LIBRE/DOCUMENTAIRE sur types d'instructions + `linkedInstructionTypeId` sur types de documents
- [x] Migration SQL v3 (2026-05-30)
- [x] **Collabora Online WOPI — Lot 1** : WopiToken, WopiController (CheckFileInfo/GetFile/PutFile), AuthFilter/SecurityConfig exemptions, docker-compose service collabora, migration v4
- [x] **Collabora Online WOPI — Lot 2** : wopi-session endpoints, CollaboraEditorComponent (iframe `[src]`), upload `.docx`, création depuis template TypeDocument, bypass zone signature pour `.docx`
- [x] **Collabora Online WOPI — Lot 3** : CollaboraConvertService, DocxSignatureService (`[SIGN_DG]`), pipeline signer() complet, invalidation WopiToken post-signature
- [x] Fix `host.docker.internal` pour WOPI server-to-server en dev local
- [x] **Session 10** : Upload gabarit `.docx` direct dans Paramètres (sans Quill/Mammoth), aperçu PDF en modale, cache PDF pré-converti dans MinIO
- [x] **Fix critique `CollaboraConvertService`** : `ByteArrayResource` avec `getFilename()` — débloque le flux `.docx` complet (soumettre + signer)
- [x] **Session 11** : Flux hybride Collabora (édition) + PDFBox (signature) — régénération automatique du PDF au save Collabora, zones-pixel restaurées pour les `.docx`, bouton « ✏️ Éditer dans Collabora » dans pdf-viewer + re-synchronisation PdfDocument

### 🔴 Priorité haute

- [ ] **Test flux complet .docx hybride** : éditer dans Collabora → enregistrer → placer zones → soumettre → DG ouvre dans pdf-viewer → optionnel : "✏️ Éditer" + correction → "✍️ Signer" → vérifier que signature/tampon apparaissent exactement dans les zones
- [ ] **Test renvoi unifié** : DG renvoie → secrétaire rouvre le `.docx` dans Collabora → re-soumet
- [ ] **Rebuild Docker** — reconstruire `dg-cockpit:latest` (Dockerfile.standalone) pour intégrer toutes les modifications des sessions 9 et 10

### 🟡 Priorité moyenne

- [ ] Entrée B flux DOCUMENTAIRE (upload d'abord) — la section "Instruction" dans la modal bureau est implémentée mais le flux "Rattacher à une instruction existante" nécessite un test complet
- [ ] Pagination des threads d'instruction (actuellement tout chargé d'un coup)
- [ ] Notification push pour l'acteur désigné quand c'est son tour

### 🟢 Priorité basse

- [ ] Migration de `ddl-auto=update` vers Flyway (après stabilisation du schéma)
- [ ] Supprimer les champs legacy en base (`correction_instruction_id`, anciens statuts instructions)
- [ ] Cache des thumbs PDF côté backend
- [ ] Tests d'intégration sur les gardes `AccesRefuseException`

---

## 11. Commandes utiles

```powershell
# Voir les logs de l'app
docker logs -f dg-cockpit-app

# Rebuild après modification code
docker build -f Dockerfile.standalone -t dg-cockpit:latest .

# Redémarrer l'app (voir §5 pour commande complète avec réseau)
docker restart dg-cockpit-app

# Accès SQL
docker exec -it dg-cockpit-postgres psql -U ged_user -d dgcockpit

# Vérifier les nouvelles colonnes (migration v3)
docker exec dg-cockpit-postgres psql -U ged_user -d dgcockpit -c "\d instruction_types"
docker exec dg-cockpit-postgres psql -U ged_user -d dgcockpit -c "\d bureau_documents"
docker exec dg-cockpit-postgres psql -U ged_user -d dgcockpit -c "\d type_documents"
docker exec dg-cockpit-postgres psql -U ged_user -d dgcockpit -c "\d instruction_messages"

# Lister les instructions avec leur type
docker exec dg-cockpit-postgres psql -U ged_user -d dgcockpit -c "SELECT i.titre, it.type_instruction, i.statut FROM instructions i LEFT JOIN instruction_types it ON i.instruction_type_id = it.id;"

# Lister les types de documents avec leur lien instruction
docker exec dg-cockpit-postgres psql -U ged_user -d dgcockpit -c "SELECT libelle, linked_instruction_type_id FROM type_documents;"

# Git
git status
git log --oneline -20
git diff master..HEAD
```

---

## 12. Contacts / Références

- **Utilisateur principal** : axexpress123456@gmail.com
- **Branche actuelle** : `appmod/java-upgrade-20260520231724`
- **Style commits** : `feat:`, `fix:`, `refactor:` en anglais court ; corps en français OK
- **Commentaires Java** : en français (convention projet)

---

*Document mis à jour le 2026-06-04 par Claude Opus 4.8 (session 11 — flux hybride Collabora/PDFBox : régénération PDF auto au save WOPI, zones-pixel pour .docx, bouton édition Collabora dans pdf-viewer, re-sync PdfDocument, abandon `[SIGN_DG]`).*
