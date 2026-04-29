#!/usr/bin/env bash
# =============================================================================
#  setup_secrets.sh — Configure les GitHub Secrets pour le CI/CD Randonnons
#  Usage : bash setup_secrets.sh [REPO] [KEYSTORE_PATH]
#  Exemple: bash setup_secrets.sh bernardhoyez/randonnons ./randonnons.jks
# =============================================================================
set -euo pipefail

REPO=${1:-"bernardhoyez/randonnons"}
KEYSTORE=${2:-"./randonnons.jks"}

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Configuration des Secrets GitHub — Randonnons CI/CD"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Vérifier que gh CLI est disponible
if ! command -v gh &>/dev/null; then
  echo "❌ GitHub CLI (gh) non trouvé. Installez-le : https://cli.github.com"
  exit 1
fi

# Vérifier authentification
if ! gh auth status &>/dev/null; then
  echo "🔑 Connexion à GitHub nécessaire..."
  gh auth login
fi

# ── Étape 1 : Générer le keystore si inexistant ───────────────────────────────
if [ ! -f "$KEYSTORE" ]; then
  echo ""
  echo "📦 Génération du keystore Android..."
  read -rp "  Alias (ex: randonnons) : " KEY_ALIAS
  read -rsp "  Mot de passe keystore : " STORE_PASS
  echo ""
  keytool -genkey -v \
    -keystore "$KEYSTORE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
    -dname "CN=Randonnons, O=BernardHoyez, C=FR"
  KEY_PASS="$STORE_PASS"
else
  read -rp "  Alias du keystore : " KEY_ALIAS
  read -rsp "  Mot de passe keystore : " STORE_PASS
  echo ""
  read -rsp "  Mot de passe de la clé : " KEY_PASS
  echo ""
fi

# ── Étape 2 : Encoder le keystore en Base64 ───────────────────────────────────
echo ""
echo "🔐 Encodage du keystore en Base64..."
KEYSTORE_B64=$(base64 -w 0 "$KEYSTORE")

# ── Étape 3 : Pousser les secrets ─────────────────────────────────────────────
echo "📤 Upload des secrets dans $REPO ..."

gh secret set KEYSTORE_BASE64  --body "$KEYSTORE_B64"  --repo "$REPO"
gh secret set KEY_ALIAS        --body "$KEY_ALIAS"     --repo "$REPO"
gh secret set KEY_PASSWORD     --body "$KEY_PASS"      --repo "$REPO"
gh secret set STORE_PASSWORD   --body "$STORE_PASS"    --repo "$REPO"

echo ""
echo "ℹ  Le secret GH_PAGES_TOKEN doit être créé manuellement :"
echo "   1. https://github.com/settings/tokens/new"
echo "   2. Scopes : repo (accès complet)"
echo "   3. gh secret set GH_PAGES_TOKEN --body <TOKEN> --repo $REPO"
echo ""
echo "✅ Secrets configurés ! Créez un tag pour publier :"
echo "   git tag v1.0.0 && git push origin main --tags"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
