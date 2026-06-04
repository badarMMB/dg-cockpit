# Mode développement — live-reload

Plus besoin de rebuild Docker à chaque modification. Stack hybride :

| Service       | Où ça tourne                  | Hot-reload                       |
| ------------- | ----------------------------- | -------------------------------- |
| **Postgres**  | Docker (`docker-compose.dev`) | n/a                              |
| **MinIO**     | Docker (`docker-compose.dev`) | n/a                              |
| **Backend**   | Hôte (`mvn spring-boot:run`)  | Spring Boot DevTools (auto-restart sur .class) |
| **Frontend**  | Hôte (`npm start`)            | Angular HMR (instant)            |

## Démarrage (3 terminaux)

### 1. Services Docker (postgres + minio)

```bash
docker compose -f docker-compose.dev.yml up -d
```

Pour les arrêter : `docker compose -f docker-compose.dev.yml down`

### 2. Backend Spring Boot

```bash
cd backend
mvn spring-boot:run
```

Disponible sur `http://localhost:8080`.
DevTools redémarre l'app dès que tu sauvegardes un `.java` (recompile via IDE ou `mvn compile`).

### 3. Frontend Angular

```bash
npm start
```

Disponible sur `http://localhost:4200`.
HMR rafraîchit instantanément à chaque sauvegarde `.ts`/`.html`/`.css`.
Les appels `/api/**` sont proxifiés vers `http://localhost:8080` (voir `proxy.conf.json`).

## Workflow type

1. Tu modifies un composant Angular → le navigateur recharge tout seul (< 1 s)
2. Tu modifies un controller Java → DevTools redémarre le contexte Spring (~ 3 s)
3. Aucun `docker build` nécessaire

## Repasser en mode prod (Docker complet)

```bash
docker compose -f docker-compose.dev.yml down
npm run build
docker compose up -d --build
```

Le JAR embarque les statiques Angular et le tout tourne sur `http://localhost:8080`.

## Fichiers ajoutés / modifiés

- `proxy.conf.json` — redirige `/api/**` du dev server vers Spring Boot
- `angular.json` — option `proxyConfig` ajoutée au serve
- `backend/pom.xml` — dépendance `spring-boot-devtools` (scope runtime, optional)
- `backend/src/main/resources/application.properties` — defaults `ged_user/ged_password` pour usage hors Docker
- `docker-compose.dev.yml` — stack minimale postgres + minio avec ports exposés
