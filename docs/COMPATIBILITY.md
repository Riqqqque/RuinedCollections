# Compatibility

RuinedCollections is built for Paper/Folia servers from the `1.21` line through `26.2`.

| Platform | Supported Versions |
| --- | --- |
| Paper | `1.21` through `26.2` |
| Folia | `1.21` through `26.2` |

## Runtime Requirements

| Requirement | Value |
| --- | --- |
| Plugin Java bytecode | `21` |
| Server Java | Use the Java version required by your Paper/Folia server version. Paper/Folia `26.1+` requires Java `25`. |
| Release build JDK | `25` |
| Release API baseline | Paper `1.21-R0.1-SNAPSHOT` |
| Latest API check | Paper `26.2.build.84-stable` |
| `plugin.yml` API version | `1.21` |
| Folia metadata | `folia-supported: true` |
| Runtime libraries | Paper library loader for SQLite and MySQL drivers |

## Latest Runtime Validation

| Platform | Tested Build | Result |
| --- | --- | --- |
| Paper `1.21` | `1.21.build.130` on Java `21` | Clean enable, validation, reload, diagnostics, and shutdown |
| Paper `26.2` | `26.2.build.84-stable` | Clean enable, validation, reload, diagnostics, and shutdown |
| Folia `26.2` | `26.2.build.1-beta` | Clean enable, validation, reload, diagnostics, and shutdown |

The same release jar is runtime-tested at both ends of the supported Paper range. PaperMC now publishes an official Folia `26.2` beta build, and RuinedCollections is runtime-tested on that build in addition to compiling against the stable Paper `26.2` API.

The release jar is intentionally compiled against the Paper `1.21` API and Java `21` bytecode. That keeps the code from accidentally relying on newer API methods that would break older supported servers. The server itself still needs the Java runtime required by that Paper/Folia version.

Paper and Minecraft now use the newer year/drop version format for current releases, which is why `26.2` is part of the supported range instead of a typo.

To compile-check the code against the latest Paper API without changing the release baseline:

```powershell
.\gradlew.bat "-PpaperApiVersion=26.2.build.84-stable" clean compileJava
```

Run that check on JDK `25`. The resulting plugin jar still targets Java `21` bytecode for the older supported server line.

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
- Minecraft versions newer than `26.2` until they are checked

Newer versions above `26.2` may still run because the plugin uses the `1.21` API baseline, but they are treated as newer than the validated support range until they are checked and released as supported.
