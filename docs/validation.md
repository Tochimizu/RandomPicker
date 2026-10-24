# Validation

## Résultats du 24 octobre 2026 — commandes, musique et navigation

Vérification locale sous Windows avec JDK 21, SDK 35 et Gradle 8.11.1, en mode hors ligne :

| Vérification | Résultat |
| --- | --- |
| `:app:assembleDebug` | Réussie ; nouvel APK dans `app/build/outputs/apk/debug/app-debug.apk` |
| `:app:testDebugUnitTest` | 32 tests réussis, 0 échec, dont les 9 nouveaux tests de `NavigationHistoryTest` |
| `:app:lintDebug` | 0 erreur, 8 avertissements, 2 informations ; analyse hors ligne |
| `:app:assembleDebugAndroidTest` | Réussie, avec les nouvelles suites `PlaybackInteractionTest` et `PlaybackNavigationTest` |
| Exécution des tests Android et recette mémoire | Non exécutées : aucun appareil ou émulateur connecté |

La passe finale utilise `-Pkotlin.compiler.execution.strategy=in-process` pour contourner un refus d'accès au démon Kotlin sous Windows, ainsi que `--offline --max-workers=2`. `ANDROID_USER_HOME` pointe vers `.tools/android-user` et le cache Gradle utilisé est celui référencé par `.tools/gradle-cache-path.txt`. Le journal local est `.tools/build-navigation-final.log`. La comparaison du nombre d'avertissements de dépendances avec une analyse en ligne n'est pas significative.

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

## Recette des nouvelles exigences — validation sur appareil restante

Les exigences F-003, F-010, F-010a et F-015 sont implémentées pour les commandes, le son de fond et les swipes. Les résultats du 22 octobre ci-dessus ne couvrent pas ces ajouts. Tous les scénarios manuels suivants ont le statut **Non exécuté sur appareil** et correspondent aux critères AC-17 à AC-23 du cahier des charges.

| Scénario | Résultat attendu |
| --- | --- |
| Ouvrir le menu dans la mosaïque, en mode immersif puis dans une image agrandie, en portrait et paysage | Stop, Next, Reload et Son restent accessibles sans quitter la vue, y compris avec TalkBack et grande police ; un accès visible rappelle le menu masqué |
| Utiliser Stop pendant une image, une animation et une vidéo avec musique de fond | Horloges, animations, vidéos et musique suspendues ; affichage et historique conservés ; reprise uniquement sur action explicite |
| Utiliser Next, Reload et Son depuis chaque vue | Next agit sur la mosaïque ou la cellule agrandie selon la vue ; Reload recommence le cycle global, rétablit les exclusions et vide l'historique sans scanner les dossiers ; Son coupe/réactive les sources sans perdre les volumes |
| Choisir, remplacer puis retirer un fichier audio depuis la sélection des dossiers | Fichier affiché et choix partagé avec les paramètres ; absence de son compatible avec le lancement de la mosaïque |
| Annuler le sélecteur audio, puis fermer et rouvrir l'application | Annulation sans modification ; choix musical sauvegardé et restauré si l'URI reste accessible |
| Suspendre les transitions automatiques puis swiper à droite et à gauche dans la grille | Mosaïque suivante puis mosaïque précédente avec les mêmes médias et la même disposition ; aucun nouveau tirage pour restaurer un état |
| Agrandir une image, swiper à droite puis à gauche avec les transitions automatiques suspendues | Média suivant puis précédent de cette cellule, sans fermer la vue agrandie ni renouveler les autres cellules par le geste |
| Laisser passer une transition automatique, puis revenir en arrière dans chaque contexte | État précédent accessible ; transitions du contexte consulté suspendues tant qu'il est dans le passé |
| Revenir plusieurs fois en arrière puis swiper à droite | États plus récents restitués dans l'ordre avant un nouveau tirage ; aucune consommation Shuffle pendant les restaurations ; Stop reste prioritaire au retour au présent |
| Dépasser 10 transitions puis remonter l'historique ; répéter avec la capacité de 5 sur appareil à mémoire réduite | Au plus 10 ou 5 retours par contexte ; entrées anciennes évincées ; swipe supplémentaire à gauche sans changement, avec indication de la limite |
| Changer de racine, sélection de dossiers, grille ou mode, puis utiliser Reload ; faire aussi une rotation | Historiques concernés réinitialisés lors des changements et du Reload ; historique conservé lors de la rotation |
| Supprimer ou exclure un fichier présent dans l'historique, puis tenter de le restaurer | Entrée ignorée avec information, sans crash ni réintroduction d'un média exclu |
| Enchaîner plusieurs centaines de swipes et retours avec images lourdes, GIF et vidéos, puis prolonger la session 30 minutes | Nombre d'entrées borné ; aucun bitmap, drawable, lecteur ou tampon retenu par l'historique ; budget de décodeurs respecté et absence de croissance mémoire continue à vérifier dans Android Studio Profiler |

`NavigationHistoryTest` couvre le bornage à 5 et 10 retours sur 1 000 états, le curseur arrière/avant, l'éviction, les réinitialisations, le filtrage des entrées invalides, les exclusions et l'absence de consommation Shuffle pendant les restaurations. `PlaybackInteractionTest` ajoute des tests Compose pour les commandes partagées, le sens des gestes, l'absence d'appui déclenché par un swipe et le composant musical partagé. `PlaybackNavigationTest` couvre le coordinateur : restauration des mosaïques, portée de la navigation en vue agrandie, Stop, historique borné, réinitialisation et callbacks obsolètes. Ces deux suites Android nécessitent encore une exécution sur appareil ou émulateur ; leur compilation ne valide pas les parcours réels.

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
