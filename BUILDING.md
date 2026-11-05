# Build Android

## Prerequis

- Java 17
- Android SDK avec la plateforme Android 35
- Aucun Gradle global n'est necessaire : le Gradle Wrapper du depot telecharge Gradle 8.9 automatiquement.

## Build local (Windows)

Depuis la racine du depot :

```powershell
.\gradlew.bat :app:assembleDebug
```

L'APK debug est genere dans `app/build/outputs/apk/debug/app-debug.apk`.

Pour compiler une release signee localement, renseigner `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` et `ANDROID_KEY_PASSWORD`, puis lancer :

```powershell
.\gradlew.bat :app:assembleRelease
```

Ne pas ajouter le keystore ni ses mots de passe au depot.

## Build automatique GitHub Actions

Le workflow `.github/workflows/android.yml` se declenche sur les push et pull requests vers `main`, ainsi que manuellement avec `workflow_dispatch`. Il installe Java 17 et la plateforme Android 35, puis utilise `./gradlew`.

Un APK debug est construit et publie comme artefact a chaque execution reussie. Sur `main`, une release signee et une GitHub Release sont aussi publiees si les quatre secrets suivants sont configures dans les parametres Actions du depot :

- `ANDROID_KEYSTORE_BASE64` : keystore encode en Base64
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Si aucun secret de signature n'est configure, seul le build debug est publie et le workflow reste valide. Une configuration partielle des secrets fait echouer explicitement le workflow.