# VaultDex — Application Vulnérable de Test

## Description du Projet

VaultDex est une application Android conçue pour simuler un gestionnaire financier et de notes sécurisées. En apparence, l'application présente une interface professionnelle construite avec Jetpack Compose et Material 3. En réalité, elle embarque volontairement un ensemble de vulnérabilités de stockage local représentatives de failles que l'on retrouve régulièrement dans des applications en production.

L'objectif de VaultDex n'est pas d'être déployée en production. Elle sert exclusivement de cible de test pour valider les capacités de détection du framework VaultGuard. Chaque couche de stockage (SharedPreferences, SQLite, fichiers internes, cache) contient des failles documentées et cataloguées.

## Vulnérabilités Implémentées

### SharedPreferences

| Fichier | Vulnérabilité | Sévérité |
|---|---|---|
| `auth_prefs.xml` | Mot de passe, token de session et email stockés en clair | Critique |
| `pin_prefs.xml` | Code PIN chiffré avec une clé AES codée en dur dans le code source | Haute |
| `api_keys.xml` | Clés API tierces (Firebase, Stripe, AWS, Twilio, SendGrid) en clair | Critique |
| `oauth_tokens.xml` | Tokens JWT (access, refresh, ID) et secret client OAuth en clair | Critique |
| `password_hash.xml` | Hash MD5/SHA1 avec sel codé en dur, question de sécurité en clair | Haute |
| `biometric_prefs.xml` | Token biométrique en clair, flag de contournement éditable | Critique |

### Base de Données SQLite

La base `vault_data.db` n'utilise pas SQLCipher. Toutes les tables sont lisibles sans mot de passe :

| Table | Données exposées |
|---|---|
| `notes` | Notes secrètes de l'utilisateur |
| `transactions` | Historique des transactions avec numéros de compte |
| `payment_cards` | Numéros de carte complets (PAN), CVV, date d'expiration |
| `messages` | Messages privés avec numéros de téléphone |
| `health_records` | Dossiers médicaux, numéro de sécurité sociale, diagnostics |
| `location_history` | Coordonnées GPS précises avec adresses physiques |

### Fichiers Internes

| Fichier | Vulnérabilité |
|---|---|
| `backup/account_backup_*.json` | Sauvegarde complète en JSON non chiffré (identifiants, notes, transactions) |
| `certs/pinned_server_cert.pem` | Certificat X.509 de pinning stocké en fichier modifiable |

### Cache

| Fichier | Vulnérabilité |
|---|---|
| `profile_cache.json` | Profil utilisateur persistant après déconnexion |
| `api_response_cache_*.log` | Journaux d'appels API contenant mots de passe et tokens |

## Architecture du Projet

```
app/src/main/java/com/vaultdex/app/
├── MainActivity.kt                    Point d'entrée de l'application
├── VaultDexApp.kt                     Classe Application (initialisation Room)
│
├── data/
│   ├── prefs/                         Gestionnaires SharedPreferences
│   │   ├── AuthPrefsManager.kt        Identifiants en clair
│   │   ├── PinCryptoManager.kt        Chiffrement AES avec clé codée en dur
│   │   ├── ApiKeyManager.kt           Clés API tierces
│   │   ├── OAuthTokenManager.kt       Tokens JWT et secret client
│   │   ├── WeakHashManager.kt         Hachage MD5/SHA1 faible
│   │   └── BiometricPrefsManager.kt   Token biométrique en clair
│   │
│   ├── db/                            Couche Room (base non chiffrée)
│   │   ├── AppDatabase.kt             Base de données principale
│   │   ├── NoteEntity.kt / NoteDao.kt
│   │   ├── TransactionEntity.kt / TransactionDao.kt
│   │   ├── PaymentCardEntity.kt       Cartes bancaires complètes
│   │   ├── MessageEntity.kt           Messages privés
│   │   ├── HealthRecordEntity.kt      Dossiers médicaux
│   │   └── LocationHistoryEntity.kt   Historique de localisation
│   │
│   ├── backup/
│   │   └── BackupManager.kt           Export JSON non chiffré
│   │
│   └── cache/
│       ├── ProfileCacheManager.kt     Cache profil persistant
│       └── LoggerCacheManager.kt      Journaux API et certificats
│
├── viewmodel/                         Gestion d'état (MVVM)
│   ├── AuthViewModel.kt
│   ├── NotesViewModel.kt
│   └── ProfileViewModel.kt
│
└── ui/                                Interface Jetpack Compose
    ├── theme/                         Thème Material 3
    ├── screens/                       Écrans (Login, Home, Notes, Pin, Profile)
    └── navigation/NavGraph.kt         Navigation Compose
```

## Déploiement

### Prérequis

- Android Studio Hedgehog (2023.1.1) ou supérieur
- JDK 17
- SDK Android 34 (API niveau 34)
- SDK Android minimum : 26 (Android 8.0)

### Installation

1. Cloner le dépôt et ouvrir le projet dans Android Studio
2. Attendre la synchronisation Gradle
3. Connecter un appareil physique ou démarrer un émulateur (API 26+)
4. Exécuter le module `app` via `Run > Run 'app'`

### Utilisation pour les Tests

1. Se connecter avec n'importe quel email et mot de passe
2. Naviguer dans l'application : ajouter des notes, configurer un PIN, exporter une sauvegarde
3. Les vulnérabilités se déclenchent automatiquement à la connexion
4. Secouer l'appareil pour ouvrir le tableau de bord VaultGuard et observer les résultats

## Licence

Ce projet est distribué sous licence MIT. Voir le fichier [LICENSE](../LICENSE) à la racine du dépôt.
