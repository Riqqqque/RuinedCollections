# Compatibility

RuinedCollections is built for Paper/Folia servers from the `1.21` line through `26.1.2`.

| Platform | Supported Versions |
| --- | --- |
| Paper | `1.21` through `26.1.2` |
| Folia | `1.21` through `26.1.2` |

## Runtime Requirements

| Requirement | Value |
| --- | --- |
| Java | `21+` |
| Release API baseline | Paper `1.21-R0.1-SNAPSHOT` |
| Latest API check | Paper `26.1.2.build.61-stable` |
| `plugin.yml` API version | `1.21` |
| Folia metadata | `folia-supported: true` |

The release jar is intentionally compiled against the Paper `1.21` API. That keeps the code from accidentally relying on newer API methods that would break older supported servers.

Paper and Minecraft now use the newer year/drop version format for current releases, which is why `26.1.2` is part of the supported range instead of a typo.

To compile-check the code against the latest Paper API without changing the release baseline:

```powershell
mvn "-Dpaper.api.version=26.1.2.build.61-stable" -DskipTests compile
```

## Startup Check

On startup, RuinedCollections writes the server name, Minecraft version, Bukkit version, and support status to the diagnostics log.

Use this command in-game or from console:

```text
/rc diagnostics
```

It will show the detected server version and whether it is inside the supported range.

## Not Supported

- Spigot or Bukkit-only servers
- Vanilla, Forge, Fabric, or NeoForge servers
- Minecraft versions below `1.21`
- Minecraft versions newer than `26.1.2` until they are checked

Newer versions above `26.1.2` may still run because the plugin uses the `1.21` API baseline, but they are treated as newer than the validated support range until they are checked and released as supported.
