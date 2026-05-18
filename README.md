# VaultGuard — Framework Universel d'Audit de Stockage Sécurisé pour Android

## Description du Projet

VaultGuard est un framework d'inspection de sécurité conçu pour auditer le stockage local de n'importe quelle application Android. Distribué sous forme de module bibliothèque indépendant, il s'intègre dans une application cible en une seule ligne de configuration Gradle et fonctionne sans aucune modification du code source de l'application hôte.

Le problème que VaultGuard adresse est concret : les développeurs Android stockent régulièrement des données sensibles (mots de passe, tokens d'authentification, clés API, données personnelles) dans des couches de stockage non sécurisées. Les SharedPreferences en mode privé, les bases de données Room sans SQLCipher, les fichiers JSON en clair dans le répertoire interne — ces pratiques sont omniprésentes dans les applications en production et constituent des vecteurs d'attaque exploitables sur un appareil rooté ou via une sauvegarde ADB.

VaultGuard automatise la détection de ces vulnérabilités. À l'exécution, il cartographie dynamiquement le sandbox de l'application, analyse chaque couche de stockage avec des heuristiques avancées (entropie de Shannon, détection de données personnelles, vérification cryptographique), et présente les résultats dans un tableau de bord Material 3 accessible par un simple geste de secousse.

Ce dépôt contient deux composants :
- **VaultGuard** (`vaultguard/`) — Le framework d'audit, cœur du projet
- **VaultDex** (`app/`) — Une application de test intentionnellement vulnérable, utilisée pour valider les capacités de détection du framework

---

## Démonstration

![Démonstration VaultGuard](demo.mp4)

---

## Pourquoi VaultGuard

Les outils d'analyse statique (lint, SAST) détectent certaines failles au moment de la compilation, mais ils ne voient pas ce qui se passe réellement à l'exécution. Un fichier SharedPreferences peut être créé dynamiquement, une base de données peut être alimentée avec des données sensibles après un appel réseau, un fichier de cache peut persister après la déconnexion de l'utilisateur.

VaultGuard opère au niveau du runtime. Il inspecte l'état réel du stockage tel qu'il existe sur l'appareil, avec les vraies données que l'application a écrites. Cette approche complémentaire permet de détecter des vulnérabilités que l'analyse statique ne peut pas identifier.

Le framework est pensé pour les développeurs et les équipes de sécurité qui souhaitent intégrer un audit de stockage dans leur cycle de développement, sans dépendre d'outils externes ou de processus manuels.

---

## Architecture Générale

```
┌─────────────────────────────────────────────────────┐
│                  Application Hôte                    │
│                                                      │
│   L'application s'exécute normalement.               │
│   VaultGuard s'initialise en arrière-plan             │
│   via un ContentProvider fusionné au manifeste.       │
└──────────────────────┬──────────────────────────────┘
                       │ Context
                       ▼
┌─────────────────────────────────────────────────────┐
│                    VaultGuard                         │
│                                                      │
│  ┌─────────────┐    ┌──────────────────────────┐     │
│  │ ShakeDetect. │    │    SandboxFileObserver    │     │
│  │ Accéléromètre│    │  Surveillance temps réel  │     │
│  └──────┬───────┘    └────────────┬─────────────┘     │
│         │                        │                    │
│         │ secousse               │ changement fichier │
│         ▼                        ▼                    │
│  ┌──────────────────────────────────────────────┐     │
│  │            ScanOrchestrator                   │     │
│  │                                               │     │
│  │  SandboxDiscovery ─► Résolution des chemins   │     │
│  │                                               │     │
│  │  ┌────────────┐ ┌────────────┐ ┌───────────┐ │     │
│  │  │ SharedPrefs │ │  Database  │ │   Files   │ │     │
│  │  │  Scanner   │ │  Scanner   │ │  Scanner  │ │     │
│  │  └─────┬──────┘ └─────┬──────┘ └─────┬─────┘ │     │
│  │        └───────────────┼──────────────┘       │     │
│  │                        ▼                      │     │
│  │              HeuristicsEngine                 │     │
│  │     Entropie | PII Regex | Crypto Verif.      │     │
│  │                        │                      │     │
│  │                        ▼                      │     │
│  │              SecurityGrader (A-F)             │     │
│  └────────────────────────┬──────────────────────┘     │
│                           │                            │
│                           ▼                            │
│  ┌──────────────────────────────────────────────┐     │
│  │         Dashboard UI (Jetpack Compose)        │     │
│  │                                               │     │
│  │  SecurityScoreCard │ FindingCard │ Console     │     │
│  └──────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────┘
```

### Couches du Framework

| Couche | Rôle | Fichiers |
|---|---|---|
| API publique | Point d'entrée, initialisation automatique, lancement du dashboard | `VaultGuard.kt`, `VaultGuardInitializer.kt`, `VaultGuardActivity.kt` |
| Découverte | Cartographie dynamique du sandbox via le Context | `SandboxDiscovery.kt` |
| Heuristiques | Analyse automatisée des valeurs découvertes | `EntropyAnalyzer.kt`, `PiiDetector.kt`, `CryptoVerifier.kt`, `HeuristicsEngine.kt` |
| Scanners | Inspection de chaque couche de stockage | `SharedPrefsScanner.kt`, `DatabaseScanner.kt`, `FileScanner.kt`, `CacheScanner.kt`, `ScanOrchestrator.kt` |
| Surveillance | Détection de changements et déclenchement gestuel | `SandboxFileObserver.kt`, `ShakeDetector.kt` |
| Modèles | Structures de données, classification, notation | `SeverityLevel.kt`, `FindingCategory.kt`, `SecurityFinding.kt`, `SecurityGrader.kt` |
| Interface | Tableau de bord Compose autonome | `DashboardScreen.kt`, `SecurityScoreCard.kt`, `FindingCard.kt`, `EvidenceConsole.kt`, `VaultGuardTheme.kt`, `VaultGuardViewModel.kt` |

---

## Moteurs d'Analyse

### Entropie de Shannon

Le calcul d'entropie mesure le degré de désordre dans une chaîne de caractères. La formule utilisée est :

```
H(X) = -Σ p(x) · log₂(p(x))
```

Un texte en langage naturel produit une entropie d'environ 3 à 4 bits par caractère. Une clé API, un token JWT ou une clé de chiffrement produit typiquement plus de 5.5 bits par caractère. VaultGuard utilise deux seuils :
- Au-dessus de 4.5 : valeur suspecte
- Au-dessus de 5.5 : probablement un secret

### Détection de Données Personnelles

Le moteur `PiiDetector` applique plus de dix familles d'expressions régulières couvrant :

- Adresses email
- Numéros de carte bancaire (Visa, Mastercard, Amex, Discover)
- Tokens JWT (structure `eyJ...` en trois parties Base64)
- Numéros de téléphone (formats internationaux et nationaux)
- Numéros de sécurité sociale
- Clés API (chaînes hexadécimales de 32 caractères ou plus)
- Matériel de clé privée (en-têtes PEM `BEGIN PRIVATE KEY`)
- Adresses IP non locales
- Blobs Base64 de grande taille
- Noms de clés sensibles (plus de 30 mots-clés : `password`, `token`, `api_key`, `cvv`, `biometric_auth_token`, etc.)

### Vérification Cryptographique

Le `CryptoVerifier` évalue l'état cryptographique de chaque valeur :

- Détection d'`EncryptedSharedPreferences` via les clés de keyset spécifiques d'AndroidX Security
- Identification de l'encodage Base64 simple (obfuscation sans chiffrement réel)
- Signalement des implémentations de chiffrement potentiellement faibles (entropie élevée en Base64 mais sans preuve d'utilisation du KeyStore Android)

---

## Système de Notation

Chaque résultat d'analyse reçoit un niveau de sévérité. Le `SecurityGrader` agrège ces résultats en un score de risque pondéré et attribue une note globale :

| Note | Plage de score | Interprétation |
|---|---|---|
| **A** | 0 | Stockage propre, aucun problème détecté |
| **B** | 1 – 20 | Problèmes mineurs, risque faible |
| **C** | 21 – 60 | Risque modéré, corrections recommandées |
| **D** | 61 – 120 | Risque élevé, corrections nécessaires |
| **F** | 121+ | Exposition critique, données sensibles en clair |

Pondérations : CRITICAL = 100, HIGH = 40, MEDIUM = 15, LOW = 5, SECURE = 0.

---

## Déploiement et Intégration

### Prérequis

- Android Studio Hedgehog (2023.1.1) ou supérieur
- JDK 17
- Android Gradle Plugin 8.4.0
- Kotlin 1.9.24
- SDK Android compilé : 34
- SDK minimum de l'application hôte : 26 (Android 8.0)

### Étape 1 — Ajouter le Module

Copier le dossier `vaultguard/` à la racine du projet Android cible, puis l'enregistrer dans `settings.gradle.kts` :

```kotlin
include(":app")
include(":vaultguard")
```

### Étape 2 — Déclarer le Plugin Bibliothèque

Dans le `build.gradle.kts` racine, s'assurer que le plugin `com.android.library` est déclaré :

```kotlin
plugins {
    id("com.android.application") version "8.4.0" apply false
    id("com.android.library") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
```

### Étape 3 — Ajouter la Dépendance

Dans le `build.gradle.kts` du module `app` :

```kotlin
dependencies {
    debugImplementation(project(":vaultguard"))
}
```

L'utilisation de `debugImplementation` garantit que VaultGuard est inclus uniquement dans les builds de débogage et n'apparaît jamais dans les APK de production.

### Étape 4 — Utiliser

Synchroniser Gradle, compiler et exécuter l'application. VaultGuard s'initialise automatiquement. Pour ouvrir le tableau de bord :

- **Par geste** : secouer l'appareil physiquement
- **Par code** : appeler `VaultGuard.launch(context)` depuis n'importe quel point de l'application

---

## Application de Test — VaultDex

Le module `app/` contient VaultDex, une application financière simulée qui embarque volontairement des vulnérabilités de stockage représentatives de failles réelles. Elle sert de terrain d'essai pour valider les capacités de détection de VaultGuard.

La documentation détaillée de VaultDex et la liste complète de ses vulnérabilités sont disponibles dans le fichier [app/README.md](app/README.md).

### Résultats Attendus

Lorsque VaultGuard audite VaultDex après une connexion et une utilisation normale de l'application, il détecte plus de 20 vulnérabilités réparties sur les quatre couches de stockage :

| Catégorie | Exemples de détections | Sévérité |
|---|---|---|
| SharedPreferences | Mot de passe en clair, tokens JWT, clés API tierces, secret client OAuth, token biométrique, flag de contournement | Critique / Haute |
| Base de données | Base non chiffrée, tables lisibles contenant cartes bancaires (PAN + CVV), dossiers médicaux, historique de localisation GPS | Critique / Haute |
| Fichiers | Sauvegarde JSON non chiffrée avec identifiants complets, certificats X.509 modifiables | Haute |
| Cache | Journaux d'API contenant mots de passe et tokens, profil utilisateur persistant après déconnexion | Critique / Haute |

Note de sécurité attendue : **F** (exposition critique).

---

## Structure du Dépôt

```
.
├── README.md                      Ce fichier
├── LICENSE                        Licence MIT
├── build.gradle.kts               Configuration Gradle racine
├── settings.gradle.kts            Déclaration des modules
├── gradle.properties              Propriétés Gradle
├── gradle/
│   └── libs.versions.toml         Catalogue de versions centralisé
│
├── vaultguard/                    Framework d'audit (25 fichiers Kotlin)
│   ├── README.md                  Documentation du framework
│   ├── build.gradle.kts           Configuration du module bibliothèque
│   ├── consumer-rules.pro         Règles ProGuard pour l'API publique
│   └── src/main/
│       ├── AndroidManifest.xml    ContentProvider et Activity déclarés
│       └── java/com/vaultguard/framework/
│           ├── core/              Modèles et notation
│           ├── discovery/         Cartographie du sandbox
│           ├── heuristics/        Moteurs d'analyse
│           ├── scanner/           Scanners de stockage
│           ├── monitor/           Surveillance temps réel
│           └── ui/                Tableau de bord Compose
│
└── app/                           Application de test VaultDex (32 fichiers Kotlin)
    ├── README.md                  Documentation de l'application vulnérable
    ├── build.gradle.kts           Configuration du module application
    └── src/main/
        └── java/com/vaultdex/app/
            ├── data/              Couches de stockage vulnérables
            ├── viewmodel/         Gestion d'état MVVM
            └── ui/                Interface Jetpack Compose
```

---

## Licence

```
MIT License

Copyright (c) 2024 VaultGuard

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
