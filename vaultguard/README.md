# VaultGuard — Framework d'Inspection de Stockage Sécurisé

## Description du Projet

VaultGuard est un framework Android distribué sous forme de module bibliothèque (`.aar`). Son rôle est d'auditer automatiquement le stockage local de n'importe quelle application Android dans laquelle il est intégré, sans nécessiter de configuration spécifique ni de connaissance préalable de l'application hôte.

Le framework fonctionne de manière totalement agnostique. À l'exécution, il résout dynamiquement le nom de package, les répertoires de données, les fichiers SharedPreferences, les bases de données SQLite, les fichiers internes et le cache de l'application hôte. Il applique ensuite une batterie d'analyses heuristiques (entropie de Shannon, détection de données personnelles par expressions régulières, vérification cryptographique) pour identifier les vulnérabilités de stockage et produire un rapport interactif.

L'intégration se fait en une seule ligne dans le fichier `build.gradle.kts` de l'application cible :

```kotlin
debugImplementation(project(":vaultguard"))
```

Aucune modification du code source de l'application hôte n'est requise. VaultGuard s'initialise automatiquement via un `ContentProvider` et se déclenche par un geste de secousse de l'appareil.

## Fonctionnalités Principales

**Découverte dynamique du sandbox** — Résolution automatique de tous les chemins de stockage via le `Context` Android, sans chemin codé en dur.

**Analyse heuristique avancée** — Trois moteurs d'analyse travaillent en parallèle :
- Calcul d'entropie de Shannon pour détecter les secrets et clés cryptographiques
- Détection de données personnelles (emails, cartes bancaires, JWT, numéros de téléphone, numéros de sécurité sociale, clés API) via plus de dix familles de patterns regex
- Vérification de l'état cryptographique des valeurs stockées (détection d'`EncryptedSharedPreferences`, identification de l'obfuscation Base64, analyse des implémentations de chiffrement faibles)

**Quatre scanners spécialisés** — SharedPreferences, bases de données SQLite, fichiers internes et cache, exécutés en parallèle via les coroutines Kotlin.

**Surveillance en temps réel** — Un `FileObserver` surveille les répertoires de données de l'application et déclenche des micro-audits automatiques lorsqu'un fichier est créé ou modifié.

**Tableau de bord Material 3** — Interface Compose autonome avec note de sécurité globale (A à F), filtres par catégorie et sévérité, cartes de résultats extensibles et console de preuves.

**Déclenchement par geste** — L'accéléromètre détecte les secousses de l'appareil pour ouvrir le tableau de bord sans interaction avec l'interface de l'application hôte.

## Architecture du Projet

### Vue d'ensemble

```
Contexte de l'application hôte
        │
        ▼
  VaultGuardInitializer (ContentProvider)
        │
        ▼
  VaultGuard (Singleton API)
        │
        ├──► ShakeDetector ──► VaultGuardActivity (Dashboard)
        │
        ├──► SandboxFileObserver ──► micro-audits en temps réel
        │
        └──► ScanOrchestrator
                │
                ├── SandboxDiscovery (résolution des chemins)
                │
                ├── SharedPrefsScanner ──┐
                ├── DatabaseScanner  ────┤ exécution parallèle
                ├── FileScanner      ────┤
                └── CacheScanner     ────┘
                        │
                        ▼
                  HeuristicsEngine
                  ├── EntropyAnalyzer
                  ├── PiiDetector
                  └── CryptoVerifier
                        │
                        ▼
                  SecurityGrader (note A-F)
                        │
                        ▼
                  Dashboard UI (Compose)
```

### Structure des fichiers

```
vaultguard/src/main/java/com/vaultguard/framework/
│
├── VaultGuard.kt                  Singleton API — point d'entrée public
├── VaultGuardInitializer.kt       ContentProvider pour auto-initialisation
├── VaultGuardActivity.kt          Activité hôte du tableau de bord
│
├── core/                          Modèles de données et notation
│   ├── SeverityLevel.kt           Niveaux de sévérité (CRITICAL à SECURE)
│   ├── FindingCategory.kt         Catégories (SharedPrefs, Database, Files, Cache)
│   ├── SecurityFinding.kt         Structure d'un résultat d'analyse
│   └── SecurityGrader.kt          Calcul de la note globale pondérée
│
├── discovery/
│   └── SandboxDiscovery.kt        Cartographie dynamique du sandbox
│
├── heuristics/                    Moteurs d'analyse
│   ├── EntropyAnalyzer.kt         Entropie de Shannon H(X)
│   ├── PiiDetector.kt             Détection PII et identifiants par regex
│   ├── CryptoVerifier.kt          Vérification de l'état cryptographique
│   └── HeuristicsEngine.kt        Orchestrateur des trois analyseurs
│
├── scanner/                       Scanners de stockage
│   ├── SharedPrefsScanner.kt      Énumération des fichiers .xml
│   ├── DatabaseScanner.kt         Sondage SQLite via sqlite_master
│   ├── FileScanner.kt             Parcours récursif de filesDir
│   ├── CacheScanner.kt            Analyse du répertoire cache
│   └── ScanOrchestrator.kt        Coordination parallèle des scans
│
├── monitor/                       Surveillance en temps réel
│   ├── SandboxFileObserver.kt     FileObserver sur les répertoires de données
│   └── ShakeDetector.kt           Détection de secousse par accéléromètre
│
└── ui/                            Interface Jetpack Compose
    ├── theme/
    │   └── VaultGuardTheme.kt     Thème Material 3 autonome
    ├── VaultGuardViewModel.kt     Gestion d'état du tableau de bord
    └── dashboard/
        ├── DashboardScreen.kt     Écran principal (filtres, liste, pull-to-refresh)
        ├── SecurityScoreCard.kt   Carte de note de sécurité (A-F)
        ├── FindingCard.kt         Carte extensible de résultat
        └── EvidenceConsole.kt     Console de preuves style terminal
```

## Fonctionnement Technique

### Découverte du Sandbox

Au lancement d'un scan, `SandboxDiscovery` interroge le `Context` Android pour construire une carte complète du sandbox de l'application :

- `context.packageName` pour le nom de package
- `context.applicationInfo.dataDir` pour le répertoire racine
- Parcours du dossier `shared_prefs/` pour lister tous les fichiers `.xml`
- `context.databaseList()` et détection par octets magiques SQLite pour les bases de données
- Parcours récursif de `context.filesDir` et `context.cacheDir` (profondeur limitée à 5, maximum 500 fichiers)

### Analyse Heuristique

Chaque valeur découverte passe par trois analyseurs :

**Entropie de Shannon** — La formule `H(X) = -Σ p(x) · log₂(p(x))` mesure le caractère aléatoire d'une chaîne. Un texte courant produit une entropie de 3 à 4 bits/caractère. Une clé API ou un token produit typiquement plus de 5.5 bits/caractère.

**Détection PII** — Dix familles d'expressions régulières couvrent les emails, numéros de carte bancaire, tokens JWT, numéros de téléphone, numéros de sécurité sociale, clés API (chaînes hexadécimales longues), matériel de clé privée, adresses IP, blobs Base64 et noms de clés sensibles (plus de 30 mots-clés comme `password`, `token`, `api_key`, `cvv`).

**Vérification cryptographique** — Le moteur détecte la présence d'`EncryptedSharedPreferences` (via les clés de keyset `__androidx_security_crypto_*`), identifie les valeurs simplement encodées en Base64 (obfuscation sans chiffrement réel), et signale les implémentations de chiffrement potentiellement faibles.

### Système de Notation

Le `SecurityGrader` calcule un score de risque pondéré :

| Sévérité | Poids |
|---|---|
| CRITICAL | 100 |
| HIGH | 40 |
| MEDIUM | 15 |
| LOW | 5 |
| SECURE | 0 |

La note finale est attribuée selon le score cumulé :

| Note | Score | Signification |
|---|---|---|
| A | 0 | Aucun problème détecté |
| B | 1 – 20 | Problèmes mineurs uniquement |
| C | 21 – 60 | Risque modéré |
| D | 61 – 120 | Risque élevé |
| F | 121+ | Exposition critique |

## Déploiement

### Prérequis

- Android Studio Hedgehog (2023.1.1) ou supérieur
- JDK 17
- SDK Android 34
- SDK Android minimum de l'application hôte : 26 (Android 8.0)

### Intégration dans une Application Existante

1. Copier le dossier `vaultguard/` à la racine du projet Android cible

2. Enregistrer le module dans `settings.gradle.kts` :
```kotlin
include(":vaultguard")
```

3. Déclarer le plugin `com.android.library` dans le `build.gradle.kts` racine :
```kotlin
plugins {
    id("com.android.library") version "8.4.0" apply false
}
```

4. Ajouter la dépendance dans le `build.gradle.kts` du module `app` :
```kotlin
dependencies {
    debugImplementation(project(":vaultguard"))
}
```

5. Synchroniser Gradle, compiler et exécuter l'application

6. Secouer l'appareil pour ouvrir le tableau de bord VaultGuard

### Utilisation Programmatique

```kotlin
// Lancer le tableau de bord manuellement
VaultGuard.launch(context)

// Déclencher un audit sans ouvrir l'interface
VaultGuard.runAudit()
```

## Licence

Ce projet est distribué sous licence MIT. Voir le fichier [LICENSE](../LICENSE) à la racine du dépôt.
