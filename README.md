# RoyalSkyblock Extensions

Adapters that teach RoyalSkyblock to use other plugins. One module per backend, one jar each, dropped
in `plugins/RoyalSkyblock/extensions/`.

| Extension | Needs | Gives RoyalSkyblock |
| --- | --- | --- |
| `EcoSkills` | EcoSkills | skill levels and stats — island mob tiers, intimidation |
| `EcoMobs` | EcoMobs | island mobs spawned from EcoMobs configs (`island-mobs.provider: ecomobs`) |
| `EcoMinions` | EcoMinions | minion triggers and conditions for perks (`minion_pickup`, `minion_place`, `minion_count_above`, …) |

---

## The idea

**Install the plugin *and* its extension.** EcoSkills alone does nothing for RoyalSkyblock; the core
carries no EcoSkills code and no check for the plugin's name. The adapter is the integration.

That is the whole point of the split. Adding MythicMobs support is a new module here — it changes
nothing in RoyalSkyblock and nothing in any other extension.

## How an adapter works

RoyalSkyblock exposes a registry, `RoyalSkyblockPlugin.integrations()`, with one SPI per kind of
backend:

- `IslandMobProvider` — spawns a configured mob by id
- `ProgressionProvider` — reads a named skill or stat for a player

An adapter registers an implementation and is done. RoyalSkyblock selects between registered backends
by id from its own config (`island-mobs.provider`, `island-mobs.progression`).

### Three rules that come from how eco loads extensions

**Register in `onEnable`.** eco enables extensions *before* the host's own enable, which is exactly
what makes this work: everything registered is in place by the time RoyalSkyblock reads it.

**Touch no host services there.** The same ordering means `islands()`, `worlds()` and `storage()` do
not exist yet. Anything needing them belongs in `onAfterLoad`.

**Never throw.** An extension that throws in `onEnable` **disables the host**. These adapters are
reflective glue against plugins that update on their own schedule, so every registration is wrapped:
a broken adapter logs and registers nothing, and the server still boots. A broken integration should
cost one jar, not the server.

## Configs

An adapter gets a config file **only when it has a decision of its own to make.**

`EcoSkills` has none, and that is correct rather than missing: it does not define skills — EcoSkills
does — and *which* skill counts as "combat" is `island-mobs.combat-skill` in RoyalSkyblock's config,
because that is a fact about island mobs, not about EcoSkills.

Where an adapter does need config, it goes in `plugins/RoyalSkyblock/extensions/<Name>/` — never the
host's folder. `Extension.getDataFolder()` returns the *host's* directory, so an adapter must build
its own path.

## Turning one off

Delete the jar. That is the real off switch.

`extensions.disabled` in RoyalSkyblock's `config.yml` also stops one loading, for isolating a fault
without file access — at the cost of a jar being present no longer meaning it is running.

## Building

```powershell
.\tools\install-deps.ps1     # once, and after any RoyalSkyblock API change
.\gradlew.bat build          # -> */build/libs/<Name>.jar
```

Requires JDK 25 to build; the adapters run inside RoyalSkyblock on a 26.2-or-newer server.
RoyalSkyblock is not published anywhere the build can reach, so `install-deps.ps1` installs the jar
the server actually runs into the local Maven repository — compiling against the running jar means an
extension meets exactly the API it was built for. eco and libreforge, by contrast, come from
`repo.auxilor.io` as published artifacts; never install those from server jars (their relocated
Kotlin is unreadable to the Kotlin compiler).

The build is **Gradle, not Maven**, for one load-bearing reason: modules that register libreforge
elements (`ecominions`) compile Kotlin against libreforge, and only the `libreforge-gradle-plugin` —
the same toolchain RoyalSkyblock itself builds with — resolves libreforge correctly. The published
`com.willfp:libreforge` Maven artifact is an empty stub. The git history of `ecominions/BLOCKED.md`
has the full autopsy.
