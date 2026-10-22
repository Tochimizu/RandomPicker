# Limites connues et recette restante

- La validation sur de vrais appareils reste nécessaire : codecs MOV/WebM, grand GIF, retrait de carte SD, perte de permission, appels, rotation, 10 000 fichiers et lecture continue pendant 30 minutes. Les objectifs de fluidité et d’absence de fuite mémoire ne sont pas présentés comme acquis.
- Le budget vidéo repose sur les capacités matérielles AVC déclarées et la classe mémoire. Il est plafonné à deux ; il n’effectue pas de mesure dynamique de charge GPU, de mémoire ou de chaque codec. Un appareil peut annoncer une capacité supérieure à sa capacité réelle sur des vidéos lourdes. Les défaillances passent par la récupération des cellules.
- Les aperçus vidéo sont extraits par Coil/MediaMetadataRetriever. Un fichier dont la miniature ne peut pas être décodée est invalidé, même si un autre lecteur aurait pu le lire.
- En Random pur, qui n’a pas de fin de cycle naturelle, les exclusions temporaires prennent fin avec la commande Recommencer le cycle. En Shuffle, elles prennent fin à l’épuisement de la file. Avec un fichier unique, un nouveau cycle peut commencer immédiatement après son exclusion.
- Lorsque tous les derniers fichiers d’un cycle Shuffle sont déjà visibles, la règle « une distribution par cycle » est prioritaire sur l’évitement des doublons simultanés. Voir l’explication dans l’architecture.
- Les positions et l’ordre exact d’une session sont conservés en mémoire lors d’une rotation, mais ne sont pas restaurés après arrêt du processus. La bibliothèque, les choix de dossiers et les préférences le sont.
- Les nouveaux fichiers ne sont découverts qu’avec Actualiser. Une erreur d’un sous-dossier est journalisée et le scan se poursuit ; ses fichiers peuvent disparaître de l’index jusqu’au prochain scan réussi.
- Le fondu concerne les images et animations. Les vidéos changent directement. Le dialogue de métadonnées peut ne pas obtenir les dimensions de certains formats animés selon le fournisseur.
- Le projet ne comprend pas de signature de production ni de publication Play Store. L’APK debug sert à la recette locale. Le SDK cible est 35 ; une publication ultérieure devra revérifier les exigences Play du moment.
