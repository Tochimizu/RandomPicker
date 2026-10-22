# Validation

## Résultats du 22 octobre 2026

Vérification locale sous Windows avec JDK 21, SDK 35, Build Tools 35.0.0 et Gradle 8.11.1 :

| Vérification | Résultat |
| --- | --- |
| `:app:assembleDebug` | Réussie ; APK debug produit |
| `:app:testDebugUnitTest` | 23 tests réussis, 0 échec |
| `:app:assembleDebugAndroidTest` | Réussie ; APK de tests produit |
| `:app:lintDebug` | 0 erreur, 18 avertissements, 2 informations |
| Signature APK (`apksigner verify`) | Valide, schéma v2 |
| Permissions de l’APK (`aapt2 dump permissions`) | Aucune permission réseau ni permission générale de stockage |
| Syntaxe du script PowerShell | Valide |
| Tests Android sur appareil / émulateur | Non exécutés |

Les avertissements Lint concernent les nouvelles versions disponibles des dépendances, des suggestions KTX et la configuration de sauvegarde Android. Les informations concernent deux états Compose pouvant utiliser un type primitif. `allowBackup=false` désactive la sauvegarde applicative ; les originaux restent hors du stockage de l’application. Le rapport détaillé est conservé dans `app/build/reports/lint-results-debug.html`.

Un cache Gradle temporaire avec un chemin plus court a été utilisé pour contourner une erreur de déplacement des transformations Gradle sous Windows. Si elle se reproduit, définir `GRADLE_USER_HOME` vers un nouveau dossier court dans le répertoire temporaire, puis relancer les mêmes tâches. Le code et les versions du projet restent identiques.

## Vérifications automatisées

Le projet contient des tests JUnit pour :

- Shuffle sur plusieurs cycles, absence de perte d’éléments, tirages globaux et appels concurrents.
- Random pur, exclusion des autres cellules, ensemble vide et fichier unique.
- Héritage de sélection, état partiel, réinclusion et 10 000 niveaux sans dépassement de pile.
- Valeurs initiales, thème sombre, volumes, ducking, priorité vidéo et modes à source unique.

Les tests Android couvrent la sélection et l’invalidation dans Room, la déduplication d’URI et l’application du thème sombre dans Compose.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Rapports : `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html` et `app/build/reports/androidTests/connected/`.

## Matrice des appareils

| Environnement | API | Statut |
| --- | --- | --- |
| Téléphone d’entrée de gamme | 29 ou plus | À tester |
| Téléphone intermédiaire | 29 ou plus | À tester |
| Téléphone haut de gamme | 29 ou plus | À tester |
| Tablette portrait/paysage | 29 ou plus | À tester |
| Émulateur Android | 29 et 35 recommandés | À tester |

Aucun résultat sur appareil ne doit être déduit du seul succès de compilation. Remplir la matrice avec modèle, RAM, version Android, codec, résolution des vidéos, nombre de cellules et durée de session.

## Protocole de recette

1. Créer un dossier avec au moins cinq niveaux, des images JPEG/PNG/WebP, GIF et WebP animés, vidéos MP4/WebM/MOV et un fichier non multimédia.
2. Choisir ce dossier. Vérifier l’indexation et les compteurs. Cocher un parent, exclure un enfant puis réinclure un petit-enfant ; vérifier le Random Set et l’état partiel.
3. Tester un ensemble vide, une image seule, puis 100 fichiers avec les quatre dispositions. Pour neuf cellules et au moins neuf fichiers, vérifier les tirages globaux distincts.
4. Tester des durées différentes et observer qu’une seule cellule change à son échéance. En Shuffle, vérifier la distribution complète d’un cycle ; vérifier le cas particulier de fin de file expliqué dans l’architecture.
5. Tester pause/reprise pendant une image, un GIF, un chargement vidéo et une vidéo en lecture. Les animations et horloges doivent rester suspendues pendant la pause.
6. Sélectionner une musique puis les cinq modes de mixage ; vérifier qu’une seule vidéo émet le son, que le ducking remonte correctement et que la position musicale est conservée.
7. Tester appel entrant, autre lecteur prenant le focus, verrouillage et passage en arrière-plan. Vérifier l’absence de son en arrière-plan.
8. Supprimer un fichier en cours de session, ajouter un fichier corrompu, retirer la carte SD et révoquer le droit sur la racine. Vérifier la récupération d’une cellule et la proposition d’autoriser de nouveau le dossier.
9. Faire tourner plusieurs fois l’appareil, changer de grille et agrandir les cellules. Observer les décodeurs et la mémoire dans Android Studio Profiler.
10. Lancer une grille 4 × 4 avec des vidéos lourdes pendant 30 minutes ; confirmer le budget de lecture et les aperçus de repli. Répéter avec un index de 10 000 fichiers et vérifier que le scan laisse l’interface réactive.
11. Fermer puis rouvrir l’application : vérifier la sélection, les durées, les volumes et le thème choisi. Sur installation neuve avec Android en thème clair, vérifier que l’application démarre en sombre.
12. Tester TalkBack, une grande taille de police, portrait/paysage et tablette.
