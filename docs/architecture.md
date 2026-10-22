# Architecture technique

## Répartition des responsabilités

| Module | Responsabilité |
| --- | --- |
| `domain/Models.kt` | Paramètres, types et sélection hiérarchique sans dépendance Android |
| `domain/RandomEngine.kt` | Distribution Random/Shuffle sérialisée par un Mutex |
| `domain/AudioPolicy.kt` | Calcul pur des volumes et de la priorité musique/vidéo |
| `data/LibraryDatabase.kt` | Index Room : racine, dossiers et fichiers |
| `data/LibraryRepository.kt` | Scan SAF, transactions, sélection, invalidation |
| `data/SettingsRepository.kt` | Préférences DataStore et mises à jour atomiques |
| `media/PlaybackCoordinator.kt` | Session, horloges indépendantes, transitions et lecteurs Media3 |
| `media/GridResourceManager.kt` | Budget de décodeurs selon la mémoire et les capacités AVC déclarées |
| `media/AudioCoordinator.kt` | Musique en boucle et audio focus système partagé |
| `ui/PickerViewModel.kt` | StateFlow, orchestration des repositories et événements UI |
| `ui/` | Écrans Compose, gestes, dialogues et thème |

L’injection est explicite par constructeurs, avec un conteneur dans `PickerApplication`. Ce projet mono-module utilise cet assemblage léger à la place de Hilt, recommandé mais non indispensable dans le cahier des charges. Les interfaces du moteur de tirage et les responsabilités séparées permettent de substituer les composants.

## Index et sélection

L’URI de document est la clé stable dans le contexte du fournisseur. Le scan utilise une file itérative, sans récursion sur la pile, et une requête groupée de colonnes pour chaque dossier. Il s’exécute sur `Dispatchers.IO`. Les erreurs de sous-dossiers sont journalisées et comptées ; une erreur sur la racine conserve l’ancien index.

Les dossiers et médias sont remplacés dans une transaction Room une fois le scan terminé. Les choix existants sont restaurés par URI lors d’un rafraîchissement de la même racine. Un changement de racine crée une nouvelle sélection et libère l’ancien droit persistant après réussite. Les mises à jour de grands ensembles de dossiers sont découpées pour respecter les limites de paramètres SQLite.

Les dossiers de premier niveau ont `parentId = null`. L’entrée des fichiers de la racine est indépendante des sous-dossiers. Les choix INCLUDE/EXCLUDE/INHERIT et l’état partiel sont évalués sans récursion. Le Random Set est filtré sur le dossier direct de chaque fichier, avec déduplication par URI, sans copie physique. Il est calculé en mémoire depuis l’index Room et n’est pas sérialisé dans DataStore.

## Tirage et concurrence

Un moteur existe par session. Les appels `next` et `nextBatch` sont protégés par le même Mutex. Un deuxième Mutex dans le coordinateur sérialise les modifications du pool, les changements de grille et les transitions.

Random tire uniformément parmi les fichiers absents des autres cellules, si ce choix est possible. Shuffle conserve une file commune et diffère les entrées déjà visibles tant qu’une alternative reste dans le cycle. Aucun élément n’est abandonné dans la file. Une mémoire courte repousse les répétitions aux frontières. Le tirage global utilise un lot atomique et exclut les fichiers déjà choisis dans ce lot.

Il existe une tension entre deux règles du cahier des charges : à la fin d’un cycle, tous les éléments restants peuvent être présents dans d’autres cellules, même avec une bibliothèque assez grande. Dans ce cas précis, cette implémentation privilégie la distribution de chaque élément une fois par cycle et autorise une répétition visible. Elle évite les doublons dans les tirages globaux lorsque le pool suffit. Une modification de la sélection ou du mode crée une nouvelle séquence.

Une exclusion temporaire retire l’entrée de la file Shuffle restante. Le prochain cycle repart de l’ensemble éligible. En Random pur, l’exclusion reste en vigueur jusqu’à la commande Recommencer le cycle, puisqu’aucune fin de file n’existe. Les fichiers invalidés par une erreur suivent un traitement distinct et restent absents jusqu’à un nouveau scan.

## Lecture et cycle de vie

Chaque cellule porte un jeton de génération. Les callbacks de chargement, d’erreur et de fin d’une ancienne génération sont ignorés. Les horloges ne progressent que pendant la lecture effective et après chargement. Elles sont suspendues avec les vidéos et animations lors d’une pause. Une image, une animation ou un aperçu statique utilise sa durée propre ; une vidéo active complète utilise sa fin Media3 ou la durée maximale choisie.

Les lecteurs utilisent le contexte Application et restent dans le ViewModel lors d’une rotation. Les PlayerView sont détachées lors de leur destruction. Supprimer ou remplacer une cellule libère son lecteur. Un watchdog de 20 secondes de temps actif couvre les chargements et les blocages prolongés en buffering. Un fichier défaillant est retiré du pool et invalidé dans Room jusqu’au prochain scan.

Les images sont dimensionnées par Coil suivant les contraintes Compose. L’animation est détectée sur le drawable réellement décodé : un WebP fixe garde la durée image et un WebP animé utilise la durée GIF. Les animations sont répétées jusqu’à la transition et arrêtées en pause. Les transitions courtes concernent les images/animations.

## Vidéos et audio

Le budget de départ est d’une vidéo sur un appareil à mémoire réduite, sinon d’au plus deux selon les capacités matérielles AVC déclarées. Les cellules restantes utilisent une image de prévisualisation et leur propre horloge. Choisir une cellule vidéo prioritaire la fait entrer dans le budget.

Tous les lecteurs vidéo sont silencieux sauf celui de la cellule audio. La politique de mixage examine la présence d’une piste audio sélectionnée sur une vidéo prête. Les volumes logiciels multiplient le niveau général et le niveau de chaque source. Ducking applique un coefficient de 0,2 à la musique ; la priorité vidéo suspend la musique sans réinitialiser sa position.

Un seul coordinateur demande l’audio focus Android pour l’ensemble des lecteurs. Une interruption transitoire suspend la lecture ; un retour de focus la reprend si l’utilisateur l’avait laissée active. Une perte définitive laisse la session en pause. L’arrière-plan abandonne le focus et suspend tous les lecteurs. Aucun service de lecture en arrière-plan n’est déclaré.

## Thème sombre

`PlaybackSettings.theme` et le décodage DataStore utilisent `ThemeMode.DARK` comme valeur absente. `PickerTheme` applique explicitement cette palette et ne consulte le thème Android que pour le choix Système. Le thème de lancement Android a également un fond sombre. Le choix est sauvegardé automatiquement.
