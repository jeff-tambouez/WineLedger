# Wine Ledger

Application Android de carnet de dégustation et de cave à vin, pour Jeff et Vanessa.
Kotlin, Jetpack Compose, Material 3, Room, Coil. Cible : Samsung Galaxy S25, Android 15.

100 % local : aucune permission INTERNET, aucun compte. Seule la sauvegarde Android copie la base dans le compte Google (voir « Sauvegardes automatiques »).

## Compiler et installer

Prérequis : Android Studio (Ladybug ou plus récent) avec le JDK 17 fourni, SDK Android 35.

1. Ouvrir le dossier `WineLedger` dans Android Studio et laisser la synchronisation Gradle se terminer (wrapper Gradle 8.9).
2. Brancher le téléphone (débogage USB activé) puis lancer la configuration `app`.
3. Pour obtenir un APK à installer directement : `./gradlew assembleRelease`.
   Résultat : `app/build/outputs/apk/release/app-release.apk`.

### Clé de signature : à faire une fois, avant tout

Android refuse de mettre à jour une app signée avec une autre clé, et la seule issue est alors de la désinstaller, **ce qui efface toute la cave**.
Sans réglage, chaque ordinateur signe avec sa propre clé de debug : compiler sur un autre PC suffit à tomber dans ce piège.

1. Sur l'ordinateur qui a compilé l'app installée sur le téléphone, copier sa clé de debug en lieu sûr (et en garder une copie ailleurs : clé USB, Drive) :
   `~/.android/debug.keystore` (Windows : `C:\Users\<toi>\.android\debug.keystore`).
2. Copier `keystore.properties.example` en `keystore.properties` à la racine du projet et y mettre le chemin de ce fichier.
   `keystore.properties`, `*.jks` et `*.keystore` sont ignorés par git.
3. Tous les builds (debug et release) utilisent alors cette clé, sur n'importe quel ordinateur. Sans `keystore.properties`, on retombe sur la clé de debug locale, comme avant.

Ne jamais utiliser une **nouvelle** clé pour l'app déjà installée : ce serait justement une signature différente.

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
- Cartes en attente chez l'autre profil (table `invitations`, sans migration) :
  - ouvrir une bouteille de la cave, ou ajouter une dégustation seul, crée « Did you also drink this bottle? » (Yes/No puis note, ou « Later ») chez l'autre profil,
    sauf s'il a déjà goûté ce vin cette année-là ou a déjà une carte pour lui ;
  - ajouter une bouteille en cave ne crée aucune carte ;
  - l'ancienne carte « Tasting to complete » (stage 2) n'est plus créée ; si une sauvegarde en contient, elle s'affiche encore avec Complete / Remove.
- Nouvelle dégustation : « Who tasted? » propose chaque profil ou Both. Avec Both, une note par profil ; une note laissée vide crée une carte « rating pending »
  (stage 1) dans ce profil. Au moins une note est requise. Notes, photo, millésime et détails de bouteille sont communs. Modifier une dégustation reste individuel.
- Photos : un appui sur une photo existante l'ouvre en grand (pincer pour zoomer, appui pour fermer).
- Ajouter une bouteille identique (même vin, millésime, format) additionne les quantités.
- Les textes de l'interface sont en anglais, comme dans le cahier des charges.

## Stats millésimes et prix

Deux blocs en bas de l'onglet Stats, communs aux deux profils : « Vintages on the table » et « What the bottles cost ».
Ils comptent toutes les bouteilles : cave (une par unité) et déjà bues. Une bouteille bue à deux ne compte qu'une fois
(par vin, année et millésime, le plus grand nombre de dégustations d'un seul profil). Les bouteilles ouvertes pas encore notées viennent du journal d'ouvertures.
Prix d'une bouteille bue : celui qu'elle avait en cave, sinon celui du journal d'ouvertures. Tranches : moins de 15, 15 à 25, 25 à 40, 40 à 70, 70 à 100, 100 et plus (borne basse incluse), plus « No price ».
Logique : `bottleMix` dans `logic/Logic.kt`.

## Cépages

- `logic/Grapes.kt` : base intégrée de ~150 cépages (rouges et blancs, avec leurs autres noms : Shiraz = Syrah, Garnacha = Grenache…), dont les plus utilisés en tête.
- Un vin garde ses cépages dans `wines.grapes` sous forme de texte lisible, pourcentage optionnel par cépage : `Merlot 70%, Cabernet Franc 30%, Petit Verdot`.
  Les anciennes listes (`Merlot, Syrah`) restent valides, les CSV/JSON/sauvegardes les reprennent tels quels, et les stats comptent les noms sans les pourcentages.
- Formulaires Cave et Journal : « Add a grape » ouvre un sélecteur avec recherche ; si le cépage n'existe pas, « Add “…” as a new grape » l'enregistre (table `custom_grapes`, incluse dans la sauvegarde) et il est ensuite proposé pour tous les vins.

## Importer une liste de vins

`tools/build_cellar_import.py` génère `tools/cellar_import.csv` (format de l'export Cave). Dans l'app : Réglages, Import, choisir ce fichier.

## Quand ça casse

- `CrashLog.kt` : un gestionnaire d'exceptions non rattrapées écrit la dernière erreur dans `last-crash.txt` (stockage privé). Au démarrage suivant, `ui/TroubleScreen.kt` l'affiche au lieu de l'app, avec la trace complète sélectionnable et un bouton pour l'exporter en `.txt`.
- Une erreur de base n'arrête plus l'app : le flux `Repo.data` est protégé par `catch`, et `ensureDefaults`/`cleanup` par `runCatching`. L'erreur devient un écran lisible, pas une fermeture.
- `data/DbRescue.kt` : copie `wineledger.db` et ses fichiers `-wal`/`-shm` dans un ZIP **sans passer par Room**, donc même quand la base ne s'ouvre plus. Bouton dans l'écran d'erreur et dans Réglages > Database.
- `tools/db_to_backup.py` : reconstruit un `backup.zip` restaurable depuis une base récupérée (le ZIP du bouton de secours, un `.db`, ou un dossier). Lit les bases v1 à v4, rejoue le journal `-wal`. Les photos étant des fichiers et non des lignes, elles ne sont pas dans une copie de base : `--photos <dossier>` les remet.
- Jamais de `fallbackToDestructiveMigration` : une migration qui échoue doit remonter l'erreur, pas effacer la cave.

## Sauvegardes automatiques

- **Sauvegarde Android** (`android:allowBackup="true"`, `res/xml/data_extraction_rules.xml`) : copie quotidienne de la base et des préférences dans le compte Google, restaurée quand l'app est réinstallée.
  Les photos n'y sont pas, car Google abandonne toute la sauvegarde d'une app au-delà de 25 Mo. Le transfert vers un nouveau téléphone, lui, prend tout, photos comprises.
  La restauration exige la même clé de signature (voir plus haut).
- **ZIP hebdomadaire** (`data/AutoBackup.kt`) : Réglages > Automatic backup > « Choose a folder » (idéalement Google Drive ou une carte SD).
  À l'ouverture de l'app, si la dernière copie a plus de 7 jours, la sauvegarde complète (base + photos, le même ZIP que « Save a full backup ») y est écrite sous le nom `wine-ledger-auto-AAAA-MM-JJ-HHMM.zip`. Les 5 plus récentes sont gardées ; les autres fichiers du dossier ne sont jamais touchés.
  Aucune copie n'est faite si la base est en erreur ou vide, pour ne jamais remplacer de bonnes copies par une mauvaise.
- Réglages > Backup affiche « Last backup: … », en rouge au-delà de 14 jours sans sauvegarde.

## Données

- Base Room `wineledger.db` (version 4), clés étrangères en cascade. La v2 ajoute `tastings.vintage` (millésime de la bouteille, distinct de l'année de dégustation) ; migrations 1→2, 2→3 et 3→4 incluses.
- Rien ne se perd entre la Cave et le Journal : à l'ouverture d'une bouteille, la dégustation reprend le millésime, les notes de la bouteille et un `BottleSnapshot` (format, prix, fenêtre de dégustation, fournisseur, date d'achat). Le même snapshot est copié sur les cartes en attente (`invitations`) et sur le journal des ouvertures (`openings`), et il est modifiable dans « Bottle details » du formulaire de dégustation. Il figure dans les exports CSV/JSON des dégustations et dans la sauvegarde ZIP.
- Photos : une par vin, JPEG 1600 px plus miniature 360 px, stockées dans le stockage privé de l'application. Aucune permission requise.
- Sauvegarde : ZIP contenant `backup.json` et `photos/`. La restauration remplace tout.

## Licences

Polices Fraunces et Inter sous SIL OFL (`licenses/`).
