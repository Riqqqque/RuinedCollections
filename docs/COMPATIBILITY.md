# Compatibility

RuinedCollections is built for the Paper/Folia `1.21` server line and is validated for:

| Platform | Supported Versions |
| --- | --- |
| Paper | `1.21`, `1.21.1`, `1.21.2` |
| Folia | `1.21`, `1.21.1`, `1.21.2` |

## Runtime Requirements

| Requirement | Value |
| --- | --- |
| Java | `21+` |
| API baseline | Paper `1.21-R0.1-SNAPSHOT` |
| `plugin.yml` API version | `1.21` |
| Folia metadata | `folia-supported: true` |

The jar is intentionally compiled against the Paper `1.21` API. That keeps the code from accidentally relying on newer API methods that would break a `1.21` or `1.21.1` server.

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

Newer versions above `1.21.2` may still run because the plugin uses the `1.21` API baseline, but they are treated as newer than the validated support range until they are checked and released as supported.
