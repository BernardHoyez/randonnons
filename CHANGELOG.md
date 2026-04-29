# Changelog — Randonnons

Toutes les modifications notables sont documentées ici.
Format : [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/)

---

## [v1.0.0] — 2026-04-28

### Ajouté
- Enregistrement GPS continu via Foreground Service (écran éteint ✓)
- Affichage carte hors-ligne depuis fichiers MBTiles (compatibles ign2mbt)
- Stats en temps réel : distance, durée, vitesse, altitude, dénivelé +/-
- Profil altimétrique interactif (MPAndroidChart)
- Gestion des waypoints : ajout, édition, suppression
- Import de traces au format GPX et KML
- Export de traces au format GPX et KML
- Bibliothèque des traces (terminées et importées) et des routes
- Gestion des cartes MBTiles dans les réglages (ajout, activation, suppression)
- Notification persistante avec distance et durée pendant l'enregistrement
- Support import GPX/KML depuis un gestionnaire de fichiers externe
- Simplification Douglas-Peucker pour l'export GPX optimisé

### Technique
- Architecture MVVM avec Room, LiveData, Coroutines
- Navigation Component avec Bottom Navigation (5 onglets)
- OSMDroid 6.1.18 pour le rendu MBTiles
- Base de données locale Room/SQLite
- FileProvider pour le partage sécurisé des exports
