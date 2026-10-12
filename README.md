# Application Android COP'S FASEG DSCHANG

Coque Android qui affiche votre portail (Apps Script) comme une vraie application : icône, plein écran,
téléchargement des PDF/Excel dans « Téléchargements », impression des reçus, envoi de photos/CNI, liens WhatsApp/téléphone.
Les données restent dans votre Google Sheets : l'application n'en stocke aucune.

## Mise en place (une seule fois, environ 15 minutes, sans Android Studio)

1. Sur GitHub (compte **COPS-FASEG0607**), créez un dépôt, par exemple `cops-faseg-android` (privé de préférence).
2. Envoyez-y tout le contenu de ce dossier (bouton **Add file > Upload files**, en gardant le dossier `.github`).
3. **URL du portail** : dans le dépôt, *Settings > Secrets and variables > Actions > onglet Variables > New repository variable*
   - Nom : `APP_URL`
   - Valeur : l'adresse de votre déploiement Apps Script se terminant par `/exec` (pas `/dev`).
4. **Clé de signature** (indispensable pour pouvoir mettre à jour l'application plus tard) :
   - Onglet **Actions > « 1 - Créer la clé de signature » > Run workflow**.
   - Quand il est terminé, téléchargez l'artefact `cle-de-signature-CONFIDENTIEL`, ouvrez `cle-a-copier.txt`.
   - Dans *Settings > Secrets and variables > Actions > Secrets*, créez 4 secrets avec les valeurs du fichier :
     `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
   - Conservez ce fichier en lieu sûr (coffre, e-mail du Président) puis supprimez-le de votre téléphone/ordinateur.
     Sans cette clé, il faudrait désinstaller l'application pour en installer une nouvelle.
5. Onglet **Actions > « 2 - Fabriquer l'APK » > Run workflow**. Après 5 à 8 minutes :
   - l'APK est dans l'artefact `COPS-FASEG-apk` et dans la section **Releases** (lien de téléchargement direct).

## Installer et partager
- Envoyez le fichier `COPS-FASEG.apk` par WhatsApp (ou le lien de la release).
- Sur le téléphone : ouvrir le fichier, autoriser « Installer des applications inconnues » pour WhatsApp/le navigateur, installer.
- Tous les membres et le Bureau ont la même application : ce que chacun voit dépend de son compte (Espace Membre ou Bureau), comme sur le web.

## Mettre à jour
- Modification du portail (Apps Script) : **rien à faire**, l'application affiche toujours la dernière version déployée
  (pensez à « Gérer les déploiements > Nouvelle version » sur le déploiement `/exec`).
- Modification de l'application elle-même : relancer l'étape 5 et renvoyer le nouvel APK ; il s'installe par-dessus l'ancien.

## À savoir
- Internet est nécessaire. Sans réseau, l'application affiche un écran « Connexion indisponible » avec un bouton Réessayer.
- Si l'URL `/exec` change (nouveau déploiement), modifiez la variable `APP_URL` et refaites l'étape 5.
- Le portail doit être déployé avec « Qui a accès : Tout le monde » pour fonctionner dans l'application.
- Le portail (Index.html, CaisseMutuelleUI.html) contient désormais un petit pont qui n'agit que dans l'application ; le site web reste inchangé.
- Dossier `.github/workflows` : s'il n'apparaît pas après l'envoi par glisser-déposer, créez les 2 fichiers via *Add file > Create new file*.
