#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# DG Cockpit — Script de déploiement (Linux / macOS)
# Prérequis : Docker + docker-compose installés
# Usage     : chmod +x deploy.sh && ./deploy.sh
# ─────────────────────────────────────────────────────────────────────────────

set -e

echo "═══════════════════════════════════════════════"
echo "  DG Cockpit — Déploiement standalone"
echo "═══════════════════════════════════════════════"

# Copier .env si absent
if [ ! -f .env ]; then
  if [ -f .env.example ]; then
    cp .env.example .env
    echo "[INFO] Fichier .env créé depuis .env.example"
    echo "[INFO] Éditez .env pour configurer SERVER_HOST, mots de passe, etc."
  fi
fi

# Arrêter les anciens conteneurs si présents
echo ""
echo "[1/3] Arrêt des conteneurs existants..."
docker compose -f docker-compose.standalone.yml down --remove-orphans 2>/dev/null || true

# Build de l'image (Angular + Java compilés à l'intérieur du Docker)
echo ""
echo "[2/3] Construction de l'image Docker (première fois : ~5 min)..."
docker compose -f docker-compose.standalone.yml build

# Démarrage
echo ""
echo "[3/3] Démarrage des services..."
docker compose -f docker-compose.standalone.yml up -d

echo ""
echo "═══════════════════════════════════════════════"
echo "  Déploiement terminé ✓"
echo ""
echo "  Application   : http://localhost:${APP_PORT:-8080}"
echo "  MinIO console : http://localhost:${MINIO_CONSOLE_PORT:-9001}"
echo ""
echo "  Logs : docker compose -f docker-compose.standalone.yml logs -f app"
echo "  Stop : docker compose -f docker-compose.standalone.yml down"
echo "═══════════════════════════════════════════════"
