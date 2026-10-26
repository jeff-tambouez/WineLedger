# Wine Ledger

Application Android de carnet de dégustation et de cave à vin, pour Jeff et Vanessa.
Kotlin, Jetpack Compose, Material 3, Room, Coil. Cible : Samsung Galaxy S25, Android 15.

100 % local : aucune permission INTERNET, aucun compte. Les données restent sur le téléphone.

## Compiler et installer

Prérequis : Android Studio (Ladybug ou plus récent) avec le JDK 17 fourni, SDK Android 35.

1. Ouvrir le dossier `WineLedger` dans Android Studio et laisser la synchronisation Gradle se terminer (wrapper Gradle 8.9).
2. Brancher le téléphone (débogage USB activé) puis lancer la configuration `app`.
3. Pour obtenir un APK à installer directement : `./gradlew assembleRelease`.
   Le build release est signé avec la clé de debug (voir `app/build.gradle.kts`) afin que l'APK soit installable sans keystore.
   Résultat : `app/build/outputs/apk/release/app-release.apk`.

Paramètres : `applicationId com.wineledger.app`, `minSdk 34`, `compileSdk 35`.

## Statut de la vérification

Le code a été écrit dans un environnement sans accès aux dépôts Maven/Google/Gradle. Le projet complet n'a donc **pas pu être compilé** là-bas.
Seule la logique pure (`Entities.kt`, `logic/Logic.kt`) a été compilée et testée sur JVM. Les imports internes ont été vérifiés par script.
Le reste (Room, Compose, ViewModel, import/export) peut demander quelques corrections mineures à la première compilation
(versions de dépendances, dérive d'API Compose). Les versions sont centralisées dans `gradle/libs.versions.toml`.

## Structure

- `data/` : entités Room, DAO, `Repo`, import/export (CSV, JSON, HTML de l'ancienne page, sauvegarde ZIP), photos, guide Veronelli.
- `logic/Logic.kt` : clés de vins, fenêtre de dégustation, regroupements, filtres, stats, CSV (sans dépendance Android).
- `AppViewModel.kt` : état unique, navigation par état, annulation (Undo) via Snackbar.
- `ui/` : thème, composants, écrans Journal, Cave, Veronelli, Stats, formulaires, Réglages.
- `assets/veronelli_2026.json` : 526 vins du guide, embarqués. `tools/` contient le script qui l'a généré.

## Choix qui s'écartent du cahier des charges ou le précisent

- Le bouton « + » ouvre directement le formulaire de dégustation sur Journal et le formulaire de bouteille sur Cave. Sur Veronelli et Stats, il propose le choix. Il est masqué dans les formulaires et les réglages.
- Invite à prix vide : propose de saisir le prix (enregistré dans le champ prix).
- Pays, région et cépages sont dans « Plus de détails ».
- L'import ajoute les lignes et ignore les doublons exacts.
- Renommer un vin le modifie sur place.
- Date d'achat : on tape les 8 chiffres (23102026), le champ affiche 23/10/2026 ; bouton « Today ». Stockée en ISO.
- Backlog « Tasting to complete » : quand un profil ajoute une nouvelle bouteille, chaque autre profil qui n'a pas encore goûté ce vin
  reçoit une carte en haut de son Journal (badge sur l'onglet) avec **Complete** (formulaire de dégustation prérempli) ou **Remove** (avec Undo).
  Réutilise la table `invitations` (`stage = 2`), donc sans migration. Les imports CSV n'en créent pas.
- Photos : un appui sur une photo existante l'ouvre en grand (pincer pour zoomer, appui pour fermer).
- Ajouter une bouteille identique (même vin, millésime, format) additionne les quantités.
- Les textes de l'interface sont en anglais, comme dans le cahier des charges.

## Cépages

- `logic/Grapes.kt` : base intégrée de ~150 cépages (rouges et blancs, avec leurs autres noms : Shiraz = Syrah, Garnacha = Grenache…), dont les plus utilisés en tête.
- Un vin garde ses cépages dans `wines.grapes` sous forme de texte lisible, pourcentage optionnel par cépage : `Merlot 70%, Cabernet Franc 30%, Petit Verdot`.
  Les anciennes listes (`Merlot, Syrah`) restent valides, les CSV/JSON/sauvegardes les reprennent tels quels, et les stats comptent les noms sans les pourcentages.
- Formulaires Cave et Journal : « Add a grape » ouvre un sélecteur avec recherche ; si le cépage n'existe pas, « Add “…” as a new grape » l'enregistre (table `custom_grapes`, incluse dans la sauvegarde) et il est ensuite proposé pour tous les vins.

## Importer une liste de vins

`tools/build_cellar_import.py` génère `tools/cellar_import.csv` (format de l'export Cave). Dans l'app : Réglages, Import, choisir ce fichier.

## Données

- Base Room `wineledger.db` (version 4), clés étrangères en cascade. La v2 ajoute `tastings.vintage` (millésime de la bouteille, distinct de l'année de dégustation) ; migrations 1→2, 2→3 et 3→4 incluses.
- Rien ne se perd entre la Cave et le Journal : à l'ouverture d'une bouteille, la dégustation reprend le millésime, les notes de la bouteille et un `BottleSnapshot` (format, prix, fenêtre de dégustation, fournisseur, date d'achat). Le même snapshot est copié sur les cartes en attente (`invitations`) et sur le journal des ouvertures (`openings`), et il est modifiable dans « Bottle details » du formulaire de dégustation. Il figure dans les exports CSV/JSON des dégustations et dans la sauvegarde ZIP.
- Photos : une par vin, JPEG 1600 px plus miniature 360 px, stockées dans le stockage privé de l'application. Aucune permission requise.
- Sauvegarde : ZIP contenant `backup.json` et `photos/`. La restauration remplace tout.

## Licences

Polices Fraunces et Inter sous SIL OFL (`licenses/`).
