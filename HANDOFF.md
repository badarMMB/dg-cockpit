# DG-Cockpit — Handoff Technique

Document de transmission pour reprise du projet par une autre IA / un nouveau développeur.

**Date du handoff :** 2026-05-26 (mis à jour — session 6 : UI workflow, données d'exemple, bug fix persistance)
**Branche courante :** `appmod/java-upgrade-20260520231724`
**Branche principale :** `master`

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
| Auth | JWT (filtre Spring + intercepteur Angular) |
| Build/Deploy | Docker multi-stage (Node 20 → Maven JDK21 → JRE21 Alpine) |

### Modèle de déploiement

Image **standalone** unique : le JAR Spring Boot sert l'API REST **ET** les fichiers Angular compilés depuis `backend/src/main/resources/static/`. Pas de Nginx, pas de service frontend séparé. Le stack docker-compose démarre 3 conteneurs : `postgres`, `minio`, `app`.

---

## 2. Rôles et workflow métier

### ⚠️ Migration en cours — deux modèles coexistent

Le projet est en cours de refonte de son modèle de rôles. **Étapes 1, 2 et 3 terminées.**

#### Ancien modèle (déprécié, encore fonctionnel)

| Rôle (enum `AppUser.Role`) | Capacités |
|------|-----------|
| `DG` | Crée des instructions, les assigne, valide ou refuse les soumissions finales |
| `SECRETAIRE` | Reçoit les courriers, gère les rendez-vous, peut envoyer des messages de clôture |
| `SUBORDONNE` | Reçoit les instructions assignées, soumet la Réponse Définitive |

#### Nouveau modèle (Étapes 1 + 2 + 3 implémentées)

Les rôles codés en dur sont remplacés par l'entité **`Poste`** (fonction dans l'organigramme) avec un ensemble d'**`Habilitation`** dynamiques. Les étapes de circuit sont définies en base via **`WorkflowStep`**. Le moteur de workflow est opérationnel côté backend ET frontend.

Voir §3 Architecture et §7 Session courante pour le détail des entités.

### Workflow d'une instruction — ancien modèle (toujours actif en prod)

```
DG crée  →  OUVERT  →  SUBORDONNE travaille  →  EN_COURS
                                                    ↓
                                                 [Rép. Définitive]
                                                    ↓
                                            SOUMIS_VALIDATION
                                                    ↓
                                       ┌────────────┴────────────┐
                                       ↓                         ↓
                                  DG valide                 DG refuse
                                       ↓                         ↓
                                   CLOTURE                    REFUSE
                                  (terminé)         (subordonné peut resoumettre)
```

### Workflow d'une instruction — nouveau modèle (opérationnel bout en bout)

```
Initiateur crée  →  BROUILLON  →  POST /api/workflow/{id}/lancer
                                        ↓
                                   EN_CIRCUIT
                                        ↓
                              [Étape N : acteur requis = Poste X]
                                        ↓ (si requiresSignature → DocumentFinalizationService)
                                    ┌───┴───┐
                                    ↓       ↓
                     POST /valider  ↓       ↓  POST /rejeter
                                  Valide  Refuse
                                    ↓       ↓
                              Étape N+1  CLOTURE_REJETE
                                    ↓
                         (plus d'étapes suivantes)
                                    ↓
                            CLOTURE_VALIDE
```

### Règles critiques (à ne PAS contourner)

1. **Les computed signals Angular remplacent les méthodes `isDG()`** — `isDG()`, `isSubordonne()`, `isSecretaire()` ont été supprimés. Utiliser `peutAgirSurEtapeActuelle()`, `peutValiderMessages()`, `peutEnvoyerRepDefinitive()`, `peutEnvoyerCloture()`, `peutCreerInstruction()`.
2. **Une instruction `CLOTURE` ne peut plus recevoir de message `FINAL`** — protection côté backend (`InstructionController.sendMessage`) qui dégrade silencieusement en message normal.
3. **Le bouton "Rép. Définitive" du Subordonné est désactivé** quand le thread est `SOUMIS_VALIDATION` ou `CLOTURE`. Il est réactif si le statut est `REFUSE`.
4. **Validate/reject de message** — le backend accepte `CAN_VALIDATE` (nouveau modèle) **OU** le rôle legacy `DG` (ancien modèle). Les deux coexistent pendant la transition.
5. **Les zones de signature doivent être brûlées dans l'image PNG côté serveur** — pas d'overlay CSS. Voir `feedback_signature_zones.md`.
6. **`DocumentFinalizationService` et le SSE `/api/events` ne doivent pas être modifiés** — conservés intacts dans la refonte workflow.

---

## 3. Architecture des fichiers

### Frontend Angular

```
src/app/
├── components/          # Composants partagés ponctuels
├── guards/              # AuthGuard, RoleGuard
├── interceptors/        # JWT interceptor (ajoute Authorization: Bearer)
├── layout/              # Layout principal (sidebar + topbar)
├── pages/
│   ├── appointments/    # Rendez-vous
│   ├── bureau/          # Bureau de la Secrétaire (documents entrants)
│   ├── chat/            # ★ Flux des instructions — entièrement connecté au nouveau workflow
│   │   ├── chat.component.ts   # Signals workflow + computed droits (Étape 3 ✅)
│   │   └── chat.component.html # Bandeau workflow + modales rejet (Étape 3 ✅)
│   ├── classeurs/       # Classement documentaire
│   ├── dashboard/       # Tableau de bord
│   ├── editor/          # Éditeur de courrier départ
│   ├── inbox/           # Courrier arrivé
│   ├── login/           # Authentification
│   ├── notes/           # Notes de service
│   ├── outbox/          # Courrier départ
│   ├── parametres/      # ★ Configuration — onglet Postes + circuit par type (Étape 3 ✅)
│   │   └── parametres.component.ts   # Inline template + toute la logique
│   ├── pdf-documents/   # Documents PDF
│   ├── settings/        # Préférences utilisateur
│   ├── signature/       # Parapheur électronique
│   └── signature-assets/# Gestion des signatures/cachets
├── services/
│   ├── api.service.ts        # ★ Toutes les méthodes workflow + Postes + WorkflowSteps (Étape 3 ✅)
│   ├── auth.service.ts       # Gestion du JWT + currentUser (AppUser avec posteId)
│   ├── notification.service.ts
│   └── toast.service.ts
└── shared/
    ├── audio-recorder/       # Enregistreur vocal (WebM/Opus)
    ├── audit-timeline/       # Timeline d'audit
    ├── pdf-file-viewer/      # ★ Visualiseur PDF page-par-page
    ├── search-bar/
    ├── toast/                # Toasts globaux
    └── workflow-timeline/    # ★ Timeline workflow instruction (Étape 3 ✅)
            ├── workflow-timeline.component.ts   # input() + effect() + computed statuts
            └── workflow-timeline.component.html # template vertical Tailwind
```

### Backend Spring Boot

```
backend/src/main/java/com/dgcockpit/
├── config/              # Sécurité, CORS, beans
├── controller/
│   ├── AuthController.java             # toUserDto null-safe sur role + posteId/posteLibelle
│   ├── GlobalExceptionHandler.java     # ★ AccesRefuseException→403, IllegalState→409
│   ├── InstructionController.java      # null-safe role, globalStatus + étapes dans DTO, soumettre legacy
│   ├── WorkflowController.java         # /api/workflow/{id}/lancer|valider|rejeter|etat
│   ├── ParametresController.java       # CRUD Poste + CRUD WorkflowStep + toUserDto avec poste
│   ├── FileController.java
│   ├── ParapheurController.java
│   ├── BureauController.java
│   └── ... (autres inchangés)
├── exception/
│   └── AccesRefuseException.java       # ★ RuntimeException → HTTP 403 (pas de Spring Security)
├── entity/
│   ├── AppUser.java           # role @Deprecated → poste (Poste) + manager (auto-relation)
│   ├── Poste.java             # ★ fonction + habilitations dynamiques
│   ├── WorkflowStep.java      # ★ étape de circuit liée à InstructionType
│   ├── Instruction.java       # statut @Deprecated → globalStatus + currentStep + currentActor
│   ├── InstructionMessage.java
│   ├── InstructionType.java   # + workflowSteps (OneToMany, circuit template)
│   ├── Assignee.java
│   └── ... (autres inchangés)
├── filter/              # JwtAuthFilter (custom — PAS Spring Security)
├── repository/
│   ├── PosteRepository.java          # ★ CRUD Postes
│   ├── WorkflowStepRepository.java   # ★ + findFirstBy... pour étape suivante
│   └── ... (autres inchangés)
├── service/
│   ├── WorkflowService.java          # ★ moteur de workflow : lancer/valider/rejeter
│   ├── ParametresService.java        # ★ CRUD Poste + WorkflowStep + posteId sur User
│   └── ... (autres inchangés)
└── sse/                 # Server-Sent Events — NE PAS MODIFIER
```

### Modèle de données actuel

```
Poste
  ├── id, code, libelle, actif
  └── habilitations : Set<Habilitation>
        (CAN_CREATE_INSTRUCTION, CAN_SIGN, CAN_VALIDATE,
         CAN_REJECT, CAN_CLOSE, CAN_MANAGE_USERS, CAN_MANAGE_TYPES, CAN_VIEW_ALL)

AppUser
  ├── (role @Deprecated — conservé pour compatibilité)
  ├── poste → Poste (ManyToOne, EAGER)
  ├── manager → AppUser (auto-relation ManyToOne, LAZY)
  ├── hasHabilitation(Poste.Habilitation) → boolean
  └── peutAgirSurEtape(WorkflowStep) → boolean  [compare poste.id == step.requiredPoste.id]

InstructionType
  └── workflowSteps → List<WorkflowStep> (OneToMany, orphanRemoval=true, ordre ASC)

WorkflowStep
  ├── instructionType → InstructionType
  ├── stepOrder (int, unique par type)
  ├── stepLabel
  ├── requiredPoste → Poste (EAGER, qui doit agir — null = étape ouverte)
  ├── requiresSignature (délègue appel DocumentFinalizationService)
  ├── requiresAttachment
  ├── timeoutJours
  └── actorInstructions (texte affiché à l'acteur dans le bandeau workflow)

Instruction
  ├── (statut @Deprecated — conservé pour compatibilité, synchronisé par WorkflowService)
  ├── globalStatus : BROUILLON | EN_CIRCUIT | CLOTURE_VALIDE | CLOTURE_REJETE
  ├── currentStep → WorkflowStep (étape courante, null si brouillon/clôturé)
  └── currentActor → AppUser (acteur désigné pour l'étape courante)
```

---

## 4. Endpoints API clés

### Authentification
- `POST /api/auth/login` — retourne `{ token, user }` ; `user.posteId` + `user.posteLibelle` inclus
- `GET /api/auth/me` — utilisateur courant

### Instructions (chat)
- `GET /api/instructions` — liste filtrée : sans `CAN_VIEW_ALL` → seulement les instructions assignées
- `POST /api/instructions` — création
- `GET /api/instructions/{id}/messages` — messages d'un thread
- `POST /api/instructions/{id}/messages` — envoi (NORMAL ou FINAL)
- `POST /api/instructions/{id}/soumettre` — legacy : SUBORDONNE soumet pour validation DG
- `PATCH /api/instructions/messages/{msgId}/validate` — accepte `CAN_VALIDATE` OU role legacy `DG`
- `PATCH /api/instructions/messages/{msgId}/reject` — idem

  **DTO `GET /api/instructions` retourne :**
  `id`, `title`, `type`, `statut` (legacy), `globalStatus`, `currentStepLabel`, `currentStepOrder`, `currentStepPosteLibelle`, `urgence`, `confidentialite`, `echeance`, `instructionTypeId`, ...

### Workflow
- `POST /api/workflow/{id}/lancer` — BROUILLON → EN_CIRCUIT
- `POST /api/workflow/{id}/valider` — valide l'étape courante (vérifie `peutAgirSurEtape`)
- `POST /api/workflow/{id}/rejeter` — body `{ "motif": "..." }` → CLOTURE_REJETE
- `GET /api/workflow/{id}/etat` — `{ id, globalStatus, currentStep, currentActor }`

  Retourne HTTP 403 (`AccesRefuseException`) si le Poste de l'acteur ne correspond pas.

### Paramètres (admin)
- `GET /api/parametres/instruction-types` (avec `?activeOnly=true`)
- `POST|PUT|DELETE /api/parametres/instruction-types/{id}`
- `GET /api/parametres/instruction-types/{typeId}/steps` — étapes du circuit
- `POST|PUT|DELETE /api/parametres/instruction-types/{typeId}/steps/{stepId}`
- `GET /api/parametres/postes` — liste des postes
- `POST|PUT|DELETE /api/parametres/postes/{id}`
- `PATCH /api/parametres/postes/{id}/toggle`
- `GET /api/parametres/users` — DTO inclut `posteId`, `posteLibelle`, `role` nullable
- `POST|PUT /api/parametres/users/{id}` — accepte `posteId` + `role` nullable dans le body

### Fichiers
- `POST /api/files/upload` → `{ name }`
- `GET /api/files/{name}` — download
- `GET /api/files/{name}/info` → `{ pageCount, name }`
- `GET /api/files/{name}/page/{n}` — rendu PNG d'une page

### SSE
- `GET /api/events` — flux (`INSTRUCTION_CREATED`, `INSTRUCTION_UPDATED`, `FILE_UPLOADED`)

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

**Ne pas** utiliser `docker run` sans réseau ni variables — l'app cherchera postgres sur `localhost:5432` depuis l'intérieur du conteneur et échouera.

```powershell
# Récupérer le nom du réseau Docker (ex: dg-cockpit_default)
docker inspect dg-cockpit-postgres --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}'

# Démarrer avec le bon réseau et les bonnes variables
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

### Variables d'environnement (overrides)

| Variable | Défaut | Notes |
|----------|--------|-------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/dgcockpit` | En Docker : utiliser le hostname du conteneur postgres |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | `ged_user` en compose |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | `ged_password` en compose |
| `MINIO_URL` | `http://localhost:9000` | Interne au réseau Docker |
| `MINIO_PUBLIC_URL` | `http://localhost:9000` | URL externe pour presigned (doit être accessible depuis le navigateur) |
| `MINIO_BUCKET` | `dgcockpit` | Bucket principal (chat, parapheur) |

Autres buckets utilisés : `ged-bureau-documents` (bureau Secrétaire).

### Dev frontend hors Docker

```powershell
npm install
npm start       # ng serve --port 4200
```
⚠️ Configurer le proxy Angular vers `http://localhost:8080` pour `/api`.

### Dev backend hors Docker

```powershell
cd backend
mvn spring-boot:run
```

---

## 6. Polices et thème

**⚠️ NE PAS réintroduire Google Fonts** dans `styles.css`. Dans l'environnement Docker / proxy d'entreprise, l'import HTTP vers `fonts.googleapis.com` retourne du HTML qui fait planter le parser OTS (erreur `invalid sfntVersion: 1008821359` = bytes `<!do`).

Stack de polices utilisée (`tailwind.config.js`) :
```js
fontFamily: {
  'sans': ['Inter', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'sans-serif'],
}
```

---

## 7. Sessions récentes — modifications appliquées

### Session 2026-05-26 (session 6) — UI workflow enrichie + données d'exemple + bug fix persistance

#### Bug critique corrigé : instructions non persistantes
**Symptôme :** Créer une instruction semblait ne pas fonctionner — elle disparaissait immédiatement.

**Cause racine (double) :**
1. `InstructionController.createInstruction()` n'initialisait jamais `globalStatus` → null en base
2. `toThreadDto()` appelait `getGlobalStatus().name()` sans null check → NPE → le GET `/api/instructions` retournait 500 → le frontend ne rechargait pas la liste

**Corrections apportées :**
- `createInstruction()` initialise maintenant `globalStatus = BROUILLON` et `statut = OUVERT` à la création
- `toThreadDto()` lignes 250-251 : null-safe sur `getStatut()` et `getGlobalStatus()`
- SQL patch : `UPDATE instructions SET global_status = 'BROUILLON' WHERE global_status IS NULL` (12 instructions existantes patchées)

#### Améliorations UI — mise en avant du workflow nouveau modèle

**`chat.component.ts`** — ajouts :
- Import `WorkflowTimelineComponent` + ajout aux `imports[]` du composant
- `circuitPreview = signal<WorkflowStep[]>([])` — aperçu du circuit dans la modale de création
- `showTimelinePanel = signal(false)` — panneau latéral droit toggleable
- `lancerApresCreation = signal(false)` — option de la modale
- `chargerCircuitPreview(typeId)` — charge les étapes du type sélectionné
- `lancerCircuitPourInstruction(id)` — méthode privée extraite pour éviter imbrication > 4 niveaux
- `createNewInstruction()` : gestion d'erreur `.error`, support du lancement immédiat, assignees vidés (remplacés par le circuit)
- `closeNewInstructionModal()` : reset des nouveaux signaux

**`chat.component.html`** — améliorations visuelles :
- Liste threads : bordure gauche colorée (`border-l-4`) selon `globalStatus` (indigo EN_CIRCUIT, vert CLOTURE_VALIDE, rouge CLOTURE_REJETE, orange BROUILLON)
- Header : bouton "Circuit" toggle (icône checklist, indigo plein quand actif)
- Panneau latéral droit (`hidden md:flex w-64`) : `app-workflow-timeline` avec infos étape courante, fermable
- Modale création : section "Participants & Résultats attendus" **supprimée** (ancien modèle)
- Modale création : **Aperçu du circuit** (étapes numérotées avec poste requis + badges Signature/PJ)
- Modale création : avertissement si type sans circuit configuré
- Modale création : checkbox "Lancer le circuit immédiatement"
- Modale création : bouton contextuel "💾 Créer en Brouillon" vs "⚡ Créer et Lancer le Circuit"

#### Données d'exemple injectées en base

**Postes créés** (IDs fixes `11111111-1111-...`) :

| Code | Libelle | Habilitations |
|------|---------|---------------|
| `DG` | Directeur Général | Toutes (8/8) |
| `CS` | Chef de Service | CREATE, VALIDATE, REJECT, VIEW_ALL |
| `INS` | Inspecteur des Douanes | CREATE |
| `CTR` | Contrôleur Douanier | CREATE |
| `SEC` | Secrétaire de Direction | CREATE, CLOSE, VIEW_ALL |

**Affectation utilisateurs → postes :**
- `dg` → DG | `secretaire` → SEC | `agent.douane` → CS
- `ibrahim_said` → CTR | `ahmed.hassan` → INS | `fatima.omar` → INS

**Circuits workflow (12 étapes sur 5 types) :**

| Type | Étapes | Particularités |
|------|--------|----------------|
| Rapport d'activité | CTR → DG | — |
| Ciblage de fraude | CTR → DG | — |
| Convocation d'agent | CS → DG | Signature DG requise |
| Suspension accès SYDONIA | INS → CS → DG | PJ requise étape 1 |
| Transmission dossier justice | INS → CS → DG | PJ étape 1 + Signature DG |

#### Analyse déséquilibres workflow (points en attente)

Problèmes identifiés mais non encore implémentés :
- `requiresSignature = true` est un stub dans `WorkflowService.appliquerSignature()` — valide sans rien faire
- Sender du message initial hardcodé `"DG"` — devrait utiliser `currentUser`
- `peutLancerCircuit()` trop permissif (tout poste peut lancer)
- Pas de mécanisme "Relancer" après `CLOTURE_REJETE`
- Pas d'indicateur "Étape X/Y" dans le bandeau
- `timeoutJours` stocké mais jamais enforced

---

### Session 2026-05-26 (session 5) — workflow-timeline + Docker rebuild

**workflow-timeline.component.html** créé (fichier manquant causant une erreur de compilation `-992008`) :
- Timeline verticale Tailwind avec `@for` + `let last = $last`
- États : spinner chargement, erreur, vide, liste des étapes
- Icônes SVG ✓/✗/numéro selon statut (PASSE/REJETE/EN_COURS/A_VENIR)
- Instructions acteur affichées seulement quand `EN_COURS`
- Badge terminal vert (`CLOTURE_VALIDE`) / rouge (`CLOTURE_REJETE`)

**Build Angular** : ✅ aucune erreur — seul warning pré-existant NG8107 dans `pdf-viewer.component.ts:48`

**Build Docker + redémarrage** :
- `docker build -f Dockerfile.standalone -t dg-cockpit:latest .` → succès (Angular prod + Maven)
- Conteneur `dg-cockpit-app` redémarré sur réseau `dg-cockpit_default`
- Spring Boot démarré en 11.7s, connexion PostgreSQL 15 OK, Tomcat port 8080

---

### Session 2026-05-26 (session 4) — Étape 3 Frontend : connexion UI au moteur de workflow

**Objectif :** Connecter l'interface Angular au nouveau moteur de workflow backend.

#### PARTIE 1 — `api.service.ts`

Interfaces exportées ajoutées :
```typescript
WorkflowPoste   { id, code, libelle }
WorkflowStep    { id, stepOrder, stepLabel, requiresSignature, requiresAttachment, actorInstructions, requiredPoste }
WorkflowActeur  { id, nomComplet }
WorkflowEtat    { id, globalStatus, currentStep, currentActor }
```

Méthodes ajoutées :
- `lancerCircuit(id)`, `validerEtape(id)`, `rejeterEtape(id, motif)`, `getEtatWorkflow(id)` → `/api/workflow`
- `soumettre(id, body)` → conservé pour legacy SUBORDONNE
- `getPostes()`, `createPoste()`, `updatePoste()`, `togglePoste()`, `deletePoste()` → `/api/parametres/postes`
- `getWorkflowSteps(typeId)`, `createWorkflowStep()`, `updateWorkflowStep()`, `deleteWorkflowStep()` → `/api/parametres/instruction-types/{typeId}/steps`

#### PARTIE 2 — `chat.component.ts` + `.html`

**chat.component.ts** — réécriture :
- Suppression de `isDG()`, `isSubordonne()`, `isSecretaire()` (méthodes publiques supprimées)
- Ajout de `workflowEtat = signal<WorkflowEtat | null>(null)`
- Computed signals :
  - `peutAgirSurEtapeActuelle()` — compare `user.posteId === workflowEtat.currentStep.requiredPoste.id`
  - `workflowEnCircuit()`, `peutLancerCircuit()`, `peutCreerInstruction()`
  - `peutEnvoyerRepDefinitive()` (remplace `isSubordonne()`), `peutEnvoyerCloture()` (remplace `isSecretaire()`), `peutValiderMessages()` (remplace `isDG()`)
- Méthodes workflow : `lancerCircuit()`, `validerEtapeWorkflow()`, `ouvrirModalRejet()`, `fermerModalRejet()`, `rejeterEtapeWorkflow()`
- `chargerMessages()` appelle aussi `getEtatWorkflow()` → met à jour `workflowEtat`
- SSE `INSTRUCTION_UPDATED` rafraîchit messages + `workflowEtat` si thread actif
- `getBadgeLabel(thread)` / `getBadgeClasses(thread)` — priorité au `globalStatus` si circuit actif/clôturé

**chat.component.html** — mise à jour complète :
- **Bandeau Workflow** (sous le header) :
  - BROUILLON + `peutLancerCircuit()` → bouton "🚀 Lancer le Circuit"
  - EN_CIRCUIT + `peutAgirSurEtapeActuelle()` → "À votre tour" + boutons Valider/Rejeter
  - EN_CIRCUIT + pas mon tour → "En attente de : [poste requis]"
  - CLOTURE_VALIDE → banner vert ✅
  - CLOTURE_REJETE → banner rouge ❌
- Badges threads : `getBadgeLabel()` / `getBadgeClasses()` (priorité globalStatus)
- Footer : `peutEnvoyerRepDefinitive()` / `peutEnvoyerCloture()` / fallback "Répondre" générique
- Cartes action finale : `peutValiderMessages()` remplace `isDG()`
- Panel documents : `peutEnvoyerRepDefinitive()` remplace `isSubordonne()`
- Bouton "Nouvelle Instruction" : `peutCreerInstruction()`
- **Modale de rejet** avec textarea motif (bouton Confirmer désactivé si vide)

#### PARTIE 3 — `parametres.component.ts` + `workflow-timeline`

Mise à jour complète (inline template) :
- **Nouvel onglet "Postes & Rôles"** — CRUD complet (code, libelle, actif)
- **Bouton "⚙️ Circuit"** sur chaque ligne de type d'instruction
- **Modale de gestion des étapes** (inline dans la même modale) :
  - Liste des étapes triées par `stepOrder`
  - Formulaire d'ajout/modification : label, ordre, Poste requis (dropdown des postes actifs), instructions acteur, checkboxes signature/pièce jointe
  - Édition inline : cliquer "Modifier" sur une étape peuple le formulaire du bas
- **Onglet Utilisateurs mis à jour** :
  - Colonne "Rôle legacy" (null → "—") + colonne "Poste" (badge teal)
  - Formulaire : champ rôle avec option "Nouveau modèle (Poste uniquement)" (value vide) + dropdown Poste (postes actifs)
  - `saveUser()` transforme role vide → null, posteId vide → null
- `AppUser` interface mise à jour : `role: UserRole | null`, `posteId: string | null`, `posteLibelle: string | null`
- Postes chargés au `ngOnInit()` (disponibles pour les dropdowns dans les modales step et user)

**workflow-timeline component** — refonte complète :
- `workflow-timeline.component.ts` : `input()` signal Angular 18 (`instructionId`, `typeId`), `effect()` en constructeur pour recharger sur changement d'input, `computed()` `etapesAffichage` + `globalStatut`, logique `calculerStatut()` pour PASSE/EN_COURS/REJETE/A_VENIR, helpers CSS `cercleCls()` / `ligneCls()` / `textCls()` avec classes Tailwind complètes
- `workflow-timeline.component.html` : timeline verticale Tailwind, `@for` avec `let last = $last`, icônes SVG ✓/✗/numéro selon statut, instructions acteur (EN_COURS seulement), badge rejet (REJETE), badge terminal vert (CLOTURE_VALIDE) / rouge (CLOTURE_REJETE)

---

### Session 2026-05-25 (session 3) — Refonte Moteur Workflow, Étape 2 backend

**Fichiers créés :** `AccesRefuseException.java`, `GlobalExceptionHandler.java`

**Fichiers réécrits :** `WorkflowController.java` (`/api/workflow`), `WorkflowService.java` (moteur complet), `ParametresService.java` (CRUD Poste + WorkflowStep), `ParametresController.java` (endpoints postes + steps)

**Fichiers modifiés :** `InstructionController.java` (null-safe role, globalStatus dans DTO, endpoint `soumettre` récupéré), `AuthController.toUserDto` (null-safe + posteId)

**Note critique :** Pas de Spring Security. Filtre custom `AuthFilter.java`. Ne jamais importer `org.springframework.security.*`. Utiliser `AccesRefuseException` pour les 403.

---

### Session 2026-05-25 (session 2) — Refonte Moteur Workflow, Étape 1

Nouvelles entités : `Poste.java`, `WorkflowStep.java`, `PosteRepository.java`, `WorkflowStepRepository.java`

Entités modifiées (rétro-compatibles) : `AppUser` (+poste, +manager), `Instruction` (+globalStatus, +currentStep, +currentActor), `InstructionType` (+workflowSteps), `InstructionMessage` (+workflowStep)

---

### Session 2026-05-25 (session 1) — Corrections workflow chat

- Split boutons toolbar : `isDG()` / `isSecretaire()` / `isSubordonne()`
- Bouton "Rép. Définitive" désactivé quand `SOUMIS_VALIDATION` ou `CLOTURE`
- Backend : rejet → `REFUSE` ; garde anti-réouverture sur thread `CLOTURE`
- Fix démarrage Docker : réseau + variables d'env explicites

### Sessions précédentes (compactées)

- Boutons validate/reject restreints au DG ; visualiseur PDF `PdfFileViewerComponent` ; endpoints `/info` et `/page/{n}` ; Google Fonts retiré

### Sessions antérieures (historique git)

- `9c82a38` — Notes de Service, routing SUBORDONNE, déploiement Docker standalone
- `6c6acdb` — Endpoint rendu PDF avec zones signature/cachet
- `cdbfc69` — Toasts + améliorations chat
- `a5b0c2a` — Système de classeurs (folder management)

---

## 8. Points d'attention / pièges connus

| Sujet | Détail |
|-------|--------|
| **Pas de Spring Security** | Filtre custom `AuthFilter.java`. Ne jamais importer `org.springframework.security.*`. Utiliser `AccesRefuseException` pour les 403. |
| **isDG() / isSubordonne() supprimés** | Ces méthodes n'existent plus dans `chat.component.ts`. Utiliser les computed signals `peutAgirSurEtapeActuelle()`, `peutValiderMessages()`, `peutEnvoyerRepDefinitive()`, `peutEnvoyerCloture()`. |
| **role: null sur AppUser Angular** | `AppUser.role` est maintenant `UserRole | null`. Tout `user.role === 'X'` doit d'abord vérifier `user.role != null`. |
| **Docker sans réseau** | `docker run` sans `--network` → app cherche postgres sur `localhost:5432` → échec. Voir §5. |
| **iframe + JWT** | Ne jamais afficher PDF via `<iframe src="/api/files/...">` → 401. Utiliser `PdfFileViewerComponent`. |
| **Google Fonts** | Ne pas réintroduire (cf. §6). |
| **Champs @Deprecated** | `AppUser.role` et `Instruction.statut` conservés pendant la migration. `@SuppressWarnings({"deprecation","removal","java:S1874"})` sur tout code qui les utilise. |
| **Migration Hibernate** | `ddl-auto=update` ajoute les nouvelles colonnes mais ne supprime pas les anciennes. Faire une migration Flyway après stabilisation. |
| **WorkflowStep.requiredPoste null** | Si null → `peutAgirSurEtape()` retourne `true` (étape ouverte à tous). À éviter en prod. |
| **Présignature MinIO** | `MINIO_PUBLIC_URL` doit être accessible depuis le navigateur client, pas le hostname interne Docker. |
| **Zones de signature** | Rasterisées dans l'image PNG par le backend (`DocumentFinalizationService`). Pas d'overlay CSS. |
| **SSE** | Si le SSE casse, le chat ne se met plus à jour temps réel. Vérifier `/api/events`. Ne pas modifier `SseService`. |

---

## 9. Mémoire IA / Conventions

Le dossier `C:\Users\User\.claude\projects\c--Users-User-Desktop-test-angular18-cyclos-nouveauTest-dg-cockpit\memory\` contient les mémoires persistantes :

- `MEMORY.md` — index
- `feedback_signature_zones.md` — règle métier sur les zones de signature

Toute IA reprenant le projet devrait :
1. Lire `MEMORY.md` au démarrage
2. Mettre à jour les mémoires `feedback`/`project` au fil des sessions
3. Respecter le style **français** dans les commits, commentaires UI, labels
4. **Lire ce HANDOFF.md en priorité** — il contient l'état de la migration en cours

---

## 10. Backlog — prochaines étapes

### ✅ Terminé

- [x] **Étape 1** : Entités `Poste`, `WorkflowStep`, migration non-destructive de `AppUser` et `Instruction`
- [x] **Étape 2** : `WorkflowService` (lancer/valider/rejeter), `WorkflowController`, CRUD Poste + WorkflowStep dans Paramètres, `InstructionController` null-safe + `globalStatus` dans DTO
- [x] **Étape 3** : `api.service.ts` (méthodes workflow + CRUD postes/steps), `chat.component` (bandeau workflow, computed signals, modale rejet), `parametres.component` (onglet Postes, gestion circuit par type, utilisateurs avec poste)
- [x] **Workflow Timeline** : `workflow-timeline.component.ts` + `.html` — affichage vertical Tailwind avec statuts PASSE/EN_COURS/REJETE/A_VENIR, spinner, erreur, badge terminal

### 🔴 Priorité haute — Validation en conditions réelles

- [ ] **Test bout en bout** : créer un `Poste`, lui assigner un utilisateur, créer un type d'instruction avec 2 étapes de circuit, créer une instruction, lancer le circuit → valider chaque étape avec le bon utilisateur → vérifier `CLOTURE_VALIDE`
- [x] **Rebuild Docker** — image reconstruite et déployée le 2026-05-26, Spring Boot démarré en 11.7s, accessible sur http://localhost:8080

### 🟡 Priorité moyenne

- [x] Affichage du circuit complet dans la timeline (`workflow-timeline` component) — statuts dynamiques via `getEtatWorkflow` + `getWorkflowSteps`
- [ ] Pagination des threads d'instruction (actuellement tout chargé d'un coup)
- [ ] Notification push (au-delà des toasts in-app) pour l'acteur désigné quand c'est son tour
- [ ] Drag-and-drop pour réordonner les étapes dans la modale circuit (actuellement saisie manuelle du `stepOrder`)

### 🟢 Priorité basse

- [ ] Migration de `ddl-auto=update` vers Flyway (après stabilisation du schéma)
- [ ] Tests d'intégration sur les gardes `AccesRefuseException`
- [ ] Cache des thumbs PDF côté backend (actuellement re-rendu à chaque ouverture)
- [ ] Supprimer les champs `@Deprecated` (`AppUser.role`, `Instruction.statut`) après validation complète de l'Étape 3

---

## 11. Commandes utiles

```powershell
# Voir les logs de l'app
docker logs -f dg-cockpit-app

# Rebuild après modification code
docker build -f Dockerfile.standalone -t dg-cockpit:latest .

# Redémarrer l'app (avec réseau + variables — voir §5 pour la commande complète)
docker stop dg-cockpit-app; docker rm dg-cockpit-app
docker run -d --name dg-cockpit-app --network dg-cockpit_default -p 8080:8080 `
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://dg-cockpit-postgres:5432/dgcockpit `
  -e SPRING_DATASOURCE_USERNAME=ged_user -e SPRING_DATASOURCE_PASSWORD=ged_password `
  -e MINIO_URL=http://dg-cockpit-minio:9000 -e MINIO_PUBLIC_URL=http://localhost:9000 `
  -e MINIO_ACCESS_KEY=minioadmin -e MINIO_SECRET_KEY=minioadmin -e MINIO_BUCKET=dgcockpit `
  dg-cockpit:latest

# Compilation backend seule (vérification rapide)
cd backend; mvn.cmd compile -q

# Accès SQL
docker exec -it dg-cockpit-postgres psql -U ged_user -d dgcockpit

# Vérifier les nouvelles tables créées par Hibernate
docker exec -it dg-cockpit-postgres psql -U ged_user -d dgcockpit -c "\dt"

# Lister les postes et utilisateurs
docker exec -it dg-cockpit-postgres psql -U ged_user -d dgcockpit -c "SELECT * FROM postes;"
docker exec -it dg-cockpit-postgres psql -U ged_user -d dgcockpit -c "SELECT id, username, nom_complet, poste_id FROM app_users;"

# Lister les étapes de circuit par type
docker exec -it dg-cockpit-postgres psql -U ged_user -d dgcockpit -c "SELECT ws.step_order, ws.step_label, p.libelle AS poste FROM workflow_steps ws LEFT JOIN postes p ON ws.required_poste_id = p.id ORDER BY ws.step_order;"

# Lister les fichiers dans MinIO
docker exec -it dg-cockpit-minio mc ls local/dgcockpit

# Git
git status
git log --oneline -20
git diff master..HEAD
```

---

## 12. Contacts / Références

- **Utilisateur principal** : axexpress123456@gmail.com
- **Branche actuelle** : `appmod/java-upgrade-20260520231724` (migration Java + refonte workflow)
- **Style commits** : `feat:`, `fix:`, `refactor:` en anglais court ; corps en français OK
- **Commentaires Java** : en français (convention projet)

---

*Document mis à jour le 2026-05-26 par Claude Sonnet 4.6 (session 6 — UI workflow, données d'exemple, bug fix persistance). À mettre à jour après chaque session significative.*
