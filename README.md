# 🥾 Randonnons

Application Android de randonnée hors-ligne — cartes MBTiles, GPS continu, GPX/KML.

![Android](https://img.shields.io/badge/Android-8.0%2B-green)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue)
![License](https://img.shields.io/badge/License-MIT-orange)

---

## Fonctionnalités

| Fonctionnalité | Détail |
|---|---|
| 🗺 Cartes hors-ligne | MBTiles (générés par [ign2mbt](https://bernardhoyez.github.io/appliz/)) |
| 📍 GPS continu | Foreground Service, enregistrement même écran éteint |
| 📊 Stats live | Distance, durée, vitesse, altitude, D+/D- |
| 📈 Profil altimétrique | Graphique interactif en temps réel |
| 🚩 Waypoints | Ajout, édition, photo, description |
| 📂 Import / Export | GPX et KML (traces et routes) |
| 📚 Bibliothèque | Historique des randonnées et routes importées |

---

## Téléchargement

👉 **[Page de téléchargement officielle](https://bernardhoyez.github.io/appliz/randonnons/)**

---

## Build local (Android Studio)

### Prérequis

- Android Studio Hedgehog (2023.1) ou plus récent
- JDK 17
- Android SDK (compileSdk 34, minSdk 26)

### Étapes

```bash
# 1. Cloner le dépôt
git clone https://github.com/bernardhoyez/randonnons.git
cd randonnons

# 2. Build debug (sans signature)
./gradlew assembleDebug

# 3. APK produit
ls app/build/outputs/apk/debug/app-debug.apk

# 4. Installer directement sur un device connecté (ADB)
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Build release signé en local

```bash
# Générer un keystore (une seule fois)
keytool -genkey -v \
  -keystore randonnons.jks \
  -alias randonnons \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# Build release
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=$(pwd)/randonnons.jks \
  -Pandroid.injected.signing.store.password=VOTRE_MOT_DE_PASSE \
  -Pandroid.injected.signing.key.alias=randonnons \
  -Pandroid.injected.signing.key.password=VOTRE_MOT_DE_PASSE
```

---

## CI/CD — GitHub Actions

Le workflow `.github/workflows/build-release.yml` gère automatiquement :

| Déclencheur | Action |
|---|---|
| Push sur `main` / `develop` | Build APK debug → artefact CI |
| Pull Request | Build debug + vérification |
| Tag `v*` (ex: `v1.0.0`) | Build APK **release signé** → GitHub Release + mise à jour page appliz |

### Configurer les Secrets GitHub

Dans **Settings → Secrets and variables → Actions**, ajouter :

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w 0 randonnons.jks` |
| `KEY_ALIAS` | Alias du keystore (ex: `randonnons`) |
| `KEY_PASSWORD` | Mot de passe de la clé |
| `STORE_PASSWORD` | Mot de passe du keystore |
| `GH_PAGES_TOKEN` | Personal Access Token avec scope `repo` (pour écrire sur `bernardhoyez.github.io`) |

### Publier une nouvelle version

```bash
# 1. Mettre à jour CHANGELOG.md avec la section ## [v1.x.y]
# 2. Committer
git add CHANGELOG.md
git commit -m "chore: prépare version v1.x.y"

# 3. Créer et pousser le tag → déclenche le workflow
git tag v1.x.y
git push origin main --tags
```

---

## Structure du projet

```
randonnons/
├── app/src/main/
│   ├── java/com/randonnons/
│   │   ├── model/          Models Room (Trace, Waypoint, Route, CarteMBTiles…)
│   │   ├── data/
│   │   │   ├── db/         DAOs + RandonnonsDatabase
│   │   │   └── repository/ RandonnonsRepository
│   │   ├── service/        GpsTrackingService (Foreground Service)
│   │   ├── ui/
│   │   │   ├── carte/      CarteFragment (OSMDroid + MBTiles)
│   │   │   ├── randonnee/  RandonneeFragment (stats live + altimétrie)
│   │   │   ├── waypoints/  WaypointsFragment
│   │   │   ├── bibliotheque/ BibliothequeFragment (import/export)
│   │   │   └── reglages/   ReglagesFragment (MBTiles + préférences)
│   │   └── util/           GeoUtils, GpxExporter/Importer, KmlExporter/Importer
│   └── res/
│       ├── layout/         Layouts XML
│       ├── navigation/     nav_graph.xml
│       ├── menu/           bottom_nav_menu.xml
│       └── xml/            file_paths.xml
└── .github/workflows/      build-release.yml
```

---

## Utiliser avec ign2mbt

Les fichiers `.mbtiles` générés par la PWA [ign2mbt](https://bernardhoyez.github.io/appliz/)
sont directement compatibles. Dans Randonnons :

1. Transférez le fichier `.mbtiles` sur votre téléphone
2. Ouvrez **Réglages → Cartes hors-ligne**
3. Appuyez sur **+ Ajouter une carte MBTiles**
4. Sélectionnez le fichier → la carte s'affiche immédiatement

---

## Licence

MIT © Bernard Hoyez
