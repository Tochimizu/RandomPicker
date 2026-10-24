# Random Media Picker — Android

Application Android native en **Kotlin et Jetpack Compose / Material 3**, réalisée à partir de `cahier_des_charges.txt`. Le **thème sombre est actif par défaut**, indépendamment du thème Android. Le choix Sombre / Clair / Système est disponible dans les paramètres et conservé dans DataStore.

## Ouvrir et compiler

Ouvrir ce dossier dans Android Studio et laisser Gradle synchroniser les dépendances. Utiliser un JDK 17 ou 21, le SDK Android 35 et les Build Tools 35.0.0. Android 10 (API 29) est la version minimale.

Versions fixées : Gradle 8.11.1, Android Gradle Plugin 8.9.2, Kotlin/Compose Compiler 2.1.20, Compose BOM 2025.04.01, Room 2.7.1, DataStore 1.1.4, Media3 1.6.1 et Coil 2.7.0. Le wrapper Gradle est inclus et sa distribution est vérifiée par SHA-256. Le premier build nécessite Internet pour télécharger les outils et dépendances ; l’application fonctionne ensuite hors ligne.

Sur Windows, avec `JAVA_HOME` défini et le SDK renseigné dans `local.properties` ou `ANDROID_HOME` :

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
```

Sur macOS/Linux : `sh ./gradlew` suivi des mêmes tâches. Un script Windows équivalent est fourni dans [`scripts/build.ps1`](scripts/build.ps1).

L’APK de test est généré dans `app/build/outputs/apk/debug/app-debug.apk`. Installation sur un appareil connecté avec le débogage USB activé :

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Pour exécuter les tests d’intégration et d’interface sur un appareil ou un émulateur :

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Le dossier `.tools/` et `local.properties` sont propres à la machine de développement et exclus du contrôle de version. Aucun SDK n’est nécessaire dans le code source livré.

## Git : ajouter, committer et pousser

Le fichier `.gitignore` exclut les outils locaux, caches, compilations, APK et clés de signature. Le wrapper Gradle et les schémas Room restent versionnés.

Initialiser le dépôt et configurer le serveur une seule fois, si ce n’est pas déjà fait :

```bash
git init -b main
git remote add origin <repository-url>
```

Depuis Git Bash sous Windows, ou Bash sous Linux/macOS :

```bash
bash bash.sh "Add fullscreen image viewer"
```

Le script ajoute les changements du projet, crée le commit puis pousse la branche courante vers `origin`, en configurant son upstream. Les suppressions sont également prises en compte. Sans changement à committer, il pousse les commits existants. Pour utiliser un autre remote : `bash bash.sh "Commit message" mon-remote`. L’authentification utilise votre configuration Git habituelle.

## Utilisation

1. Dans **Bibliothèque**, choisir un dossier local avec le sélecteur Android. Le scan inclut tous les sous-dossiers accessibles et conserve l’interface réactive.
2. Déplier les dossiers et cocher ceux à inclure. Cocher un parent applique son choix à ses descendants ; une modification ultérieure d’un enfant reste possible. Les fichiers directement dans la racine ont leur propre entrée. Choisir éventuellement un **son de fond** dans la Bibliothèque ; son nom est affiché et il peut être remplacé ou retiré. Le choix est partagé avec les paramètres.
3. Lancer la **Mosaïque**. Les cellules renouvellent leur contenu indépendamment, avec une grille de 1 × 1 à 4 × 4. Les durées commencent lorsque le média est prêt.
4. Un appui simple sur une image ou une animation l’ouvre en plein écran, avec les barres système masquées. Un nouvel appui sur l’image, la croix ou le bouton Retour revient à la mosaïque. L’appui court choisit aussi la cellule audio ; pour les vidéos, le double appui ouvre la vue agrandie. Un appui long propose Suivant, Revoir, Exclure temporairement et Informations.
5. Les commandes **Stop / Précédent / Next / Reload / Son** restent accessibles dans la mosaïque, en mode immersif et dans la vue agrandie. Stop suspend la lecture et affiche la commande de reprise ; Next avance dans la vue active ; Reload recommence le cycle global et rétablit les exclusions temporaires ; Son coupe ou réactive toutes les sources. La barre de la mosaïque permet aussi de modifier la grille et de passer en mode immersif.
6. Dans **Paramètres**, régler Shuffle/Random, durées de 1 à 120 secondes, lecture vidéo complète ou limitée, cadrage, transitions, musique locale en boucle, mixage, volumes, rotation et thème.

Une seule vidéo peut émettre le son. Le mode automatique choisit une vidéo active ; désactiver cette option pour verrouiller la cellule choisie. Les modes Musique seule, Vidéo seule, Priorité vidéo, Ducking et Mixage sont disponibles. Le volume système Android n’est pas modifié.

La mosaïque, les animations et le son se mettent en pause lorsque l’application perd le premier plan, lorsque l’écran se verrouille ou pendant la navigation hors du lecteur. Le choix des dossiers et les réglages sont restaurés après fermeture ; la session de lecture est conservée lors d’une rotation.

## Navigation par swipe et historique

Les interactions suivantes sont implémentées (F-003, F-010, F-010a et F-015) ; leur recette sur appareil reste à effectuer :

- Un menu **Stop / Next / Reload / Son** accessible dans la mosaïque, en mode immersif et dans l'image agrandie. Stop suspend la lecture et l'audio ; Next avance dans la vue active ; Reload recommence le cycle global et vide l'historique ; Son coupe ou réactive toutes les sources.
- Le choix facultatif d'un **son de fond directement dans la sélection des dossiers**, avec remplacement et retrait, partagé avec les paramètres.
- Un **swipe vers la droite pour avancer**, vers la gauche pour revenir : mosaïque suivante/précédente dans la grille, média suivant/précédent de la cellule dans l'image agrandie, en restant dans cette vue. Après un retour, avancer restitue d'abord les états déjà visités.
- Un historique limité à **10 retours par contexte**, réduit à **5 sur les appareils à mémoire limitée**, conservant uniquement des références et métadonnées. À la limite, le retour ne change plus l'affichage. La consultation du passé suspend les transitions automatiques du contexte consulté ; Stop empêche leur reprise automatique.

Les commandes restent visibles. Le bouton **Précédent** fournit une alternative au swipe. Un changement de dossiers, de grille ou de mode, ainsi que Reload, réinitialise l'historique ; une rotation le conserve. La recette est détaillée dans [`docs/validation.md`](docs/validation.md).

## Confidentialité et accès

Le manifeste ne demande aucune permission réseau ni permission générale de stockage. L’application utilise uniquement les URI accordées par le Storage Access Framework, en lecture. Les originaux ne sont ni copiés, ni modifiés, ni supprimés. Le sélecteur est limité aux documents locaux ; les limitations du fournisseur Android restent applicables.

Le bouton Actualiser réindexe le dossier. Une erreur de lecture invalide le fichier dans l’index et provoque un nouveau tirage ; un nouveau scan permet de retenter ce fichier. L’autorisation racine est vérifiée au démarrage et à chaque retour au premier plan.

## Documentation et validation

- [`docs/architecture.md`](docs/architecture.md) : modules, données et règles de lecture.
- [`docs/validation.md`](docs/validation.md) : commandes, protocole de recette et matrice des appareils.
- [`docs/limitations.md`](docs/limitations.md) : limites et écarts restant à valider.

Références utilisées : [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files), [audio focus Android](https://developer.android.com/media/optimize/audio-focus), [compatibilité AGP 8.9](https://developer.android.com/build/releases/agp-8-9-0-release-notes), [Compose avec Kotlin 2](https://developer.android.com/jetpack/androidx/releases/compose-kotlin), [images animées Coil](https://coil-kt.github.io/coil/gifs/).
