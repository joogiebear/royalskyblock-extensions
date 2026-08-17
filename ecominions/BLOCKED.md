# EcoMinions adapter — written, not building

The code here is complete. It does not compile, and the reason is the build toolchain rather than
anything in these files.

## What makes this one different

Every other adapter talks to **eco** and to **RoyalSkyblock**, both of which expose an ordinary
Java-visible API. This one registers **libreforge elements** — four triggers and a condition — so it
has to compile against libreforge itself, in Kotlin, because a `Condition` is an abstract Kotlin class
whose `arguments` come from a DSL.

## Why Maven cannot do it

Two routes, both closed:

**The published artifact is empty.** `com.willfp:libreforge:2026.33` from `repo.auxilor.io` is a
**261-byte, 2-entry jar** containing no classes — the same empty-stub pattern already documented in
this codebase for `com.willfp:EcoMobs`. The real classes are in `libreforge.core:common`, which is in
no public repository:

```
Could not find artifact libreforge.core:common:jar:2026.33 in auxilor
```

**The shipped jar is shaded.** Installing `plugins/libreforge/versions/libreforge-2026.33.jar` into
`~/.m2` gets the classes, but its Kotlin is relocated to `com.willfp.eco.libs.kotlin`, which leaves
the `@Metadata` unreadable to the Kotlin compiler. Java modules survive this — the Java-visible API is
intact, which is why the EcoSkills and EcoMobs adapters build fine against the shipped eco jar — but
Kotlin does not:

```
Unresolved reference 'arguments'.
Unresolved reference 'get'.
'description' overrides nothing.
```

**And a Java port is not a way out.** `com.willfp.libreforge.Arguments` does not exist under that
name, and `Condition`'s `description`, `categories` and `arguments` are not Java-visible on the class.
The Kotlin API is the only API.

> Note for anyone debugging this: the local repository wins over remote ones, so a hand-installed
> copy silently shadows the published artifact and the failure then looks like the *published* one
> being broken. If Kotlin compilation fails on unresolved libreforge references, check
> `~/.m2/repository/com/willfp` first.

## The way forward

**Build the extensions with Gradle**, using `com.willfp.libreforge-gradle-plugin` — which is exactly
what RoyalSkyblock itself does, and exactly why it can compile Kotlin against libreforge when this
cannot. That plugin exists to resolve libreforge properly, and the core's `build.gradle.kts` is a
working, proven configuration to copy, including the Kotlin relocation that makes eco plugins share
one Kotlin runtime.

This is not specific to minions: **any** future adapter that registers libreforge elements will hit
the same wall, so the toolchain question wants answering once rather than per-adapter.

## Meanwhile

The minion triggers and the `minion_count_above` condition are **still in the RoyalSkyblock core and
still working**. Nothing was removed there. Moving them is the only outstanding piece of taking
third-party integrations out of the core.
