# Consumer proguard rules for VaultGuard library
# These rules are applied to the consuming app when minification is enabled.

# Keep all VaultGuard public API
-keep class com.vaultguard.framework.VaultGuard { *; }
-keep class com.vaultguard.framework.VaultGuardInitializer { *; }
-keep class com.vaultguard.framework.VaultGuardActivity { *; }
