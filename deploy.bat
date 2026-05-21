@echo off
REM ─────────────────────────────────────────────────────────────────────────────
REM DG Cockpit — Script de déploiement (Windows)
REM Prérequis : Docker Desktop installé et démarré
REM ─────────────────────────────────────────────────────────────────────────────

echo ═══════════════════════════════════════════════
echo   DG Cockpit ^— Déploiement standalone
echo ═══════════════════════════════════════════════

REM Copier .env si absent
if not exist .env (
    if exist .env.example (
        copy .env.example .env
        echo [INFO] Fichier .env créé depuis .env.example
        echo [INFO] Éditez .env pour configurer SERVER_HOST, mots de passe, etc.
    )
)

REM Arrêter les anciens conteneurs si présents
echo.
echo [1/3] Arrêt des conteneurs existants...
docker compose -f docker-compose.standalone.yml down --remove-orphans 2>nul

REM Build
echo.
echo [2/3] Construction de l'image Docker (première fois : ~5 min)...
docker compose -f docker-compose.standalone.yml build
if errorlevel 1 (
    echo [ERREUR] Le build a échoué.
    pause
    exit /b 1
)

REM Démarrage
echo.
echo [3/3] Démarrage des services...
docker compose -f docker-compose.standalone.yml up -d
if errorlevel 1 (
    echo [ERREUR] Le démarrage a échoué.
    pause
    exit /b 1
)

echo.
echo ═══════════════════════════════════════════════
echo   Déploiement terminé !
echo.
echo   Application   : http://localhost:8080
echo   MinIO console : http://localhost:9001
echo.
echo   Logs : docker compose -f docker-compose.standalone.yml logs -f app
echo   Stop : docker compose -f docker-compose.standalone.yml down
echo ═══════════════════════════════════════════════
pause
