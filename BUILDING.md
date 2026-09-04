# hidemodels — building

## Why there is no Fabric Loom here

Minecraft 26.2 has **no named mapping source at all**:

| source | 26.2 |
|---|---|
| yarn | no build published |
| intermediary | `mappings.tiny` is a 25-byte header, **zero** class entries |
| Mojang official | version json ships only `client` + `server`, no `client_mappings` |

That is not a problem to work around — it is the reason none is needed. The shipped client jar is
already in readable official names, and because intermediary is an identity mapping, **what you
compile against is exactly what runs**. Loom exists to remap; with nothing to remap it only blocks
the build. Loom 1.17.20 fails at configuration time with
`Failed to find official mojang mappings for 26.2`.

So this is a plain `java` Gradle build, with `Fabric-Loom-Remap: false` in the manifest so Loader
does not try to remap the result.

## Where the compile classpath comes from

There is no `mavenCentral()` artifact for the Minecraft client, and it is not redistributable, so
`downloadMinecraft` fetches it the way a launcher does: Mojang's `version_manifest_v2.json`, then
the `26.2` version json, then `downloads.client` plus every non-native entry in `libraries`. Each
file is checked against the sha1 the manifest publishes and written via a `.part` file, so an
interrupted build cannot leave a truncated jar behind. Results are cached in
`build/minecraft/26.2/` — about 112 MB, downloaded once.

Fetching from the manifest is what keeps the build portable: nothing here depends on a local game
installation, and the versions are whatever 26.2 actually ships rather than whatever a launcher
happens to have lying around (a hand-maintained classpath had guava 31.0.1 and gson 2.11.0 against
26.2's real 33.6.0 and 2.14.0).

All 59 non-native libraries go on the classpath rather than a hand-picked list, because MC's own
public signatures leak types this mod never references — every omission costs a build to discover.

## Toolchain

* **Gradle 9.7.1**, via the committed wrapper — just run `./gradlew`. Gradle 8.x's Groovy cannot
  run on Java 25 (`Unsupported class file major version 69`), and Gradle 7.x rejects Java 17+
  class files outright.
* **JDK 25** — the 26.2 client jar is class-file major 69, so the mod targets 25, and Gradle must
  *itself* run on 25 since it also compiles. Point `JAVA_HOME` at one, or list it in
  `org.gradle.java.installations.paths` in `gradle.properties`.
* `hidemodels.mixins.json` must declare `compatibilityLevel: JAVA_25`. With `JAVA_21` Mixin
  refuses to load mixin classes compiled to major 69, at startup. Mixin 0.8.7 does list
  `JAVA_25`, so this is only a matter of saying so.

```sh
export JAVA_HOME=/path/to/jdk-25
./gradlew build       # -> build/libs/hidemodels-1.0.0.jar
```

Then copy the jar into your `mods/` folder. CI does the same thing on `ubuntu-latest` with
`temurin` 25; see `.github/workflows/build.yml`.

## Two 26.2 API details worth remembering

* `Display.ItemDisplay#getItemStack()` is **private** → reached with an `@Invoker` accessor mixin.
* The component type is `net.minecraft.resources.Identifier`, **not** `ResourceLocation`.
* `EntityRenderDispatcher#shouldRender(E, Frustum, double, double, double)` is the hook: returning
  false skips the entity before its render state is built, so a hidden piece costs nearly nothing.

MC's own annotated signatures leak `org.jspecify.annotations.Nullable` and
`com.mojang.serialization.Codec`, so both must be on the compile classpath even though the mod
references neither.
