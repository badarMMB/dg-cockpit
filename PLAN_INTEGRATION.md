# Plan d'intégration — DG Cockpit
> Mis à jour au fur et à mesure des commits. Légende : ✅ Terminé · 🔄 En cours · ⬜ À faire

---

## Avancement global
| Phase | Fonctionnalité | Statut | % |
|-------|---------------|--------|---|
| 0 | Infrastructure Docker (PostgreSQL + MinIO + SSE) | ✅ | 100% |
| 1 | Circuit de validation (workflow) | ✅ | 100% |
| F | Complétion frontend (UI/UX transversale) | ✅ | 100% |
| 2 | Vue Calendrier (Agenda) | ✅ | 100% |
| 3 | Recherche globale | ✅ | 100% |
| 4 | Modèles de courriers | ✅ | 100% |
| 5 | Audit trail | ✅ | 100% |
| 6 | Analytics dashboard | ✅ | 100% |
| 7 | Messages vocaux (MinIO + MediaRecorder) | ✅ | 100% |
| 8 | Signature électronique (canvas simple) | ✅ | 100% |
| 9 | Module GED — Signature visuelle PDF | ✅ | 100% |

---

## Phase 0 — Infrastructure ✅
- [x] Docker Compose : PostgreSQL 16-alpine + MinIO + App
- [x] Réutilisation des conteneurs `ged-postgres` et `ged-minio`
- [x] SSE (Server-Sent Events) pour les notifications temps réel
- [x] MinIO service + FileController (upload / presigned URL)
- [x] Build multi-stage (Angular → Maven → Runtime)

---

## Phase 1 — Circuit de validation ✅

### 1.1 Backend — Entité & Repository
- [x] Créer entité `ValidationStep` (instructionId, validateur, statut, commentaire, date)
- [x] Créer `ValidationStepRepository`
- [x] Ajouter statut `SOUMIS_VALIDATION` à `Instruction.StatutInstruction`

### 1.2 Backend — Controller & Service
- [x] Créer `WorkflowController` : POST `/api/instructions/{id}/soumettre`
- [x] Créer `WorkflowController` : POST `/api/instructions/{id}/valider`
- [x] Créer `WorkflowController` : POST `/api/instructions/{id}/rejeter`
- [x] Broadcast SSE `STATUT_CHANGE` à chaque transition
- [x] Ajouter endpoint GET `/api/instructions/{id}/workflow` (historique des étapes)

### 1.3 Frontend — Composant Workflow
- [x] Créer `WorkflowTimelineComponent` (affiche les étapes passées/en cours)
- [x] Panneau workflow droit (desktop) + drawer bas (mobile) dans `chat.component`
- [x] Bouton "Soumettre pour validation" visible si statut OUVERT/EN_COURS
- [x] Boutons "Valider / Rejeter" visibles si statut SOUMIS_VALIDATION
- [x] Badge de statut coloré dans la liste des threads (sidebar chat)
- [x] `canSoumettre()` / `canValider()` calculés dynamiquement
- [x] Statut du thread mis à jour localement après chaque action workflow

### 1.4 Tests & commit
- [x] `ng build --configuration production` sans erreur
- [x] Build Docker réussi et container démarré

---

## Phase F — Complétion frontend (UI/UX transversale) ✅

### F.1 Chat (`/chat`)
- [x] Header dynamique : titre, type, agent et badge statut tirés du thread actif
- [x] Bouton 🔄 pour ouvrir/fermer le panneau de validation
- [x] Messages vides et état "aucun dossier sélectionné" gérés

### F.2 Sidebar
- [x] Cloche 🔔 connectée au `NotificationService` avec badge rouge du compteur
- [x] Mini-panneau de notifications déroulant
- [x] Highlight actif corrigé pour sous-routes (`/inbox/123` → "Courrier Arrivé" reste actif)

### F.3 Dashboard
- [x] Badges de statut colorés pour la liste des instructions récentes
- [x] `statutColors` + `statutLabels` (Ouvert, En cours, En validation, Clôturé, Refusé)

### F.4 Build
- [x] Correction notation crochet `n['instructionId']` (index signature Angular strict)
- [x] `ng build --configuration production` propre

---

## Phase 2 — Vue Calendrier ✅

### 2.1 Backend
- [x] Endpoint GET `/api/rendez-vous/calendrier?mois=YYYY-MM` (groupés par jour)
- [x] `findByDateBetweenOrderByDateAscHeureAsc` dans `RendezVousRepository`

### 2.2 Frontend
- [x] Calendrier CSS natif (grille 7 colonnes, lundi-dimanche)
- [x] Toggle Liste / Calendrier (signal `viewMode`) dans `appointment-list`
- [x] `calendarDays` computed : grille du mois courant avec padding lundi-based
- [x] `rdvByDay` computed : regroupement des RDV par date ISO
- [x] Clic sur un jour → `selectedDay` signal → filtre `filteredRdv`
- [x] Dot indicateur sur les jours ayant des RDV
- [x] Navigation mois (prevMonth / nextMonth)
- [x] `getRendezVousCalendrier()` ajouté dans `ApiService`

### 2.3 Tests & commit
- [x] Build Docker réussi et container démarré

---

## Phase 3 — Recherche globale ✅

### 3.1 Backend
- [x] Endpoint GET `/api/search?q=...` (interroge instructions + courriers + RDV + collaborateurs)
- [x] Résultats typés : `{ type, id, titre, apercu }`

### 3.2 Frontend
- [x] Créer `SearchBarComponent` dans la sidebar/topbar
- [x] Dropdown de résultats avec icône par type
- [x] Clic → navigation vers le détail (`/inbox/:id`, `/chat`, etc.)
- [x] Debounce 300ms, minimum 2 caractères

### 3.3 Tests & commit
- [x] Commit : `feat: recherche globale multi-modules`

---

## Phase 4 — Modèles de courriers ✅

### 4.1 Backend
- [x] Entité `TemplateCourrierDepart` (nom, objet, contenu)
- [x] CRUD `/api/templates`
- [x] Seeder : 3 templates par défaut (convocation, réponse, note de service)

### 4.2 Frontend
- [x] Modal "Nouveau courrier" avec liste déroulante de templates
- [x] Pré-remplissage automatique objet + contenu sur sélection
- [x] Gestion des templates dans Paramètres

### 4.3 Tests & commit
- [x] Commit : `feat: modèles de courriers départ`

---

## Phase 5 — Audit trail ✅

### 5.1 Backend
- [x] Entité `AuditLog` (entityType, entityId, action, acteur, details, createdAt)
- [x] `AuditService.log()` injecté dans WorkflowController, CourrierArriveController, CourrierDepartController
- [x] Endpoint GET `/api/audit` (50 derniers) + GET `/api/audit/{entityType}/{entityId}`

### 5.2 Frontend
- [x] `AuditTimelineComponent` (shared) — timeline verticale avec dots
- [x] Section "Historique" dans InboxDetail et OutboxDetail
- [x] Onglet "Journal d'Audit" dans Paramètres (50 événements globaux)
- [x] `getAuditLogs()` + `getAuditByEntity()` ajoutés dans `ApiService`

---

## Phase 6 — Analytics dashboard ✅

### 6.1 Backend
- [x] `/api/dashboard/stats` étendu : `instructionsByStatut`, `courrierArriveByStatut`, `courrierDepartByStatut`, totaux, RDV du jour réel
- [x] `countByStatut()` ajouté aux 3 repositories

### 6.2 Frontend
- [x] Section analytics (3 cartes) sous le tableau principal
- [x] Barres de progression CSS Tailwind par statut pour Instructions, Courrier Arrivé, Courrier Départ
- [x] Totaux dynamiques depuis l'API
- [x] `instrChartRows`, `caChartRows`, `cdChartRows` computed signals

---

*Document mis à jour le 2026-05-15.*

---

## Phase 7 — Messages vocaux ✅

### 7.1 Frontend
- [x] Créer `AudioRecorderComponent` (bouton micro, timer, waveform CSS animée)
- [x] `MediaRecorder` API → blob WebM/opus, événement `(recorded)` émis
- [x] Upload via `ApiService.uploadFile()` → MinIO dans `onAudioRecorded()`
- [x] Lecteur `<audio>` inline dans les bulles de message (`msg.audioUrl`)
- [x] Intégré dans la barre de saisie du chat (à côté de 📎)

### 7.2 Backend
- [x] `audioUrl` ajouté à `InstructionMessage` entity + getter/setter
- [x] `sendMessage` : persistence de `audioUrl` + `attachmentName`
- [x] `toMessageDto` : expose `audioUrl` dans la réponse JSON

### 7.3 Tests & commit
- [x] Build Docker réussi

---

## Phase 8 — Signature électronique ✅

### 8.1 Frontend
- [x] `SignatureModalComponent` : canvas HTML5 avec mouse + touch events
- [x] Export `canvas.toDataURL('image/png')` → `fetch()` → `Blob` → `File`
- [x] Upload MinIO via `ApiService.uploadFile()` avec fallback gracieux
- [x] Tampon "Scellé ✓ + date/heure" dans la zone de signature du document
- [x] Spinner pendant l'upload, état `isSaving` signal

### 8.2 Backend
- [x] Aucun changement nécessaire (MinIO FileController déjà opérationnel)

---

---

## Phase 9 — Module GED : Signature visuelle PDF ✅

> Remplace la signature canvas simple (Phase 8) par un module complet d'annotation visuelle inspiré du projet GED.
> Buckets MinIO dédiés : `ged-signatures`, `ged-stamps`, `ged-documents`, `ged-final-documents`.

### 9.1 Backend — Infrastructure & stockage

- [x] Ajouter PDFBox 3.0.3 dans `pom.xml`
- [x] Étendre `MinioService` : `ensureBucket()` au démarrage pour les 4 nouveaux buckets, `uploadBytes()`, `downloadBytes()`, `presignedUrl(bucket, key)`, `delete(bucket, key)`

### 9.2 Backend — Entités & Repositories

- [x] Entité `UserSignatureAsset` — id UUID, userId, assetType (SIGNATURE | STAMP), bucket, objectKey, originalFileName, contentType, fileSize, active, createdAt, updatedAt
- [x] Entité `PageAnnotation` — id UUID, pageId (`"{docId}::{pageNum}"`), annotationType, signatureAssetId nullable, textContent nullable, xPercent, yPercent, widthPercent, heightPercent, createdBy, createdAt, updatedAt
- [x] Entité `PdfDocument` — id UUID, title, originalFileName, bucket, objectKey, pageCount, status (DRAFT | FINALIZED), finalizedObjectKey nullable, createdAt, updatedAt
- [x] `UserSignatureAssetRepository` — `findByUserIdAndActiveTrue()`
- [x] `PageAnnotationRepository` — `findByPageId()`, `deleteByPageId()`
- [x] `PdfDocumentRepository` — `findAllByOrderByCreatedAtDesc()`

### 9.3 Backend — Services

- [x] `SignatureImageProcessingService` — suppression fond (seuil R/G/B > 230 → alpha = 0), crop marges transparentes, sortie PNG transparent
- [x] `SignatureAssetService` — validation type, traitement image, upload MinIO (ged-signatures / ged-stamps), persistance DB, URL présignée
- [x] `DocumentFinalizationService` — rendu page PNG à 150 DPI (`PDFRenderer`), fusion annotations PDFBox (`LosslessFactory.createFromImage` pour PNG transparent, conversion Y-axis flip), stockage PDF final dans `ged-final-documents`

### 9.4 Backend — Controllers REST

- [x] `SignatureAssetController` — `GET/POST /api/users/me/signature-assets`, `GET /{id}/url`, `DELETE /{id}`
- [x] `AnnotationController` — `GET /api/pages/{pageId}/annotations`, `POST/PUT/DELETE /api/annotations`
- [x] `PdfDocumentController` — `GET/POST /api/pdf-documents`, `GET /{id}/pages/{n}/image`, `POST /{id}/finalize`, `GET /{id}/final`, `DELETE /{id}`

### 9.5 Frontend — Services & Routing

- [x] `fabric@5.3.0` + `@types/fabric@5.3.6` ajoutés dans `package.json`
- [x] `ApiService` étendu : méthodes signature assets, annotations, pdf-documents
- [x] Routes ajoutées : `/signature-assets`, `/pdf-documents`, `/pdf-viewer/:id`
- [x] Sidebar : 2 nouveaux items "Signatures & Cachets" (🖊️) et "Documents PDF" (📄)

### 9.6 Frontend — Composants

- [x] `SignatureAssetsComponent` (`/signature-assets`) — grille de cards avec preview PNG transparent, filtrage SIGNATURE / STAMP, upload avec prévisualisation, suppression
- [x] `PdfDocumentsComponent` (`/pdf-documents`) — tableau des documents, import PDF, bouton Annoter, badge statut (Brouillon / Finalisé), téléchargement PDF final
- [x] `PdfViewerComponent` (`/pdf-viewer/:id`) — canvas Fabric.js, panneau gauche (thumbnails pages + liste assets), drag & drop asset → canvas, resize/déplacement avec poignées, `object:modified` → sync annotation backend, suppression annotation, bouton Finaliser, lien téléchargement PDF final

### 9.7 Docker

- [x] `npm install` → `package-lock.json` mis à jour (fabric)
- [x] `docker compose build && docker compose up -d` — rebuild complet
- [x] Vérification : interface accessible, upload signature, annotation PDF, finalisation

---

*Document mis à jour le 2026-05-15 — Projet DG-Cockpit entièrement finalisé.*
