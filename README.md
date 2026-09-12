# Hide Models

A client-side Fabric mod for **Minecraft 1.20.5 – 26.2** that stops chosen [ModelEngine](https://mythiccraft.io/index.php?resources/modelengine.1/)
model pieces from rendering — a mount's head that blocks your view, a pet that follows you around,
a visual effect you'd rather not see — chosen by their `item_model` id.

It **cancels rendering only**. Entities are never removed, so hitboxes, interactions and the
server's view of the world are all untouched.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.0 or newer, or NeoForge.
2. Drop the jar **covering your Minecraft version** into `mods/`. Each jar names the range it
   covers — `hidemodels-1.5.0+mc26.1-26.2-fabric.jar`, `hidemodels-1.5.0+mc1.21.5-1.21.8-fabric.jar`
   — and declares that range, so Loader refuses the wrong one rather than failing later.

**Why a range rather than one jar per version.** Across each range the compiled mod is
byte-identical: the same classes, the same mixins, differing only in the metadata that names the
range. Shipping four files instead of eighteen is therefore not a compromise, it is the truth about
what was built — and a CI job rebuilds every version on every push and fails if any of them stops
matching the group whose jar it would ship under.

Requirements, all declared in `fabric.mod.json`:

| | |
|---|---|
| Minecraft | the range the jar names, e.g. `>=1.21.5 <=1.21.8`. Bounded at both ends: it claims nothing it was not built against |
| Fabric Loader | `>=0.19.0` — or Quilt Loader, which runs the same jar |
| Java | `>=25` on 26.x, `>=21` on 1.21.x — each version's own requirement |
| Fabric API | **required** on Fabric and Quilt. Not on NeoForge, where its work is done by NeoForge's own events |
| Other mods | none |

Client-only (`"environment": "client"`), so there is nothing to install server-side.

**Why the range is bounded at both ends.** An open `>=1.21.5` would claim versions this jar has
never been built or tested against, and the failure that produces is the worst kind: a 1.21.x jar is
remapped to one version's intermediary names, and a mixin whose target moved does not fail loudly —
it simply never applies, so the mod hides nothing with nothing in the log to explain it. Every jar
therefore states exactly the span it was built and tested across, and Loader refuses anything
outside it.

## Which versions it can target

**1.20.5 through 26.2** — eighteen releases — from one source tree with no preprocessor, and with
exactly three small files that differ per version —
every mixin target and every vanilla type the mod names resolves on all of them, checked against
each version's own client jar rather than assumed. CI runs that matrix on every push.

| Minecraft | source | notes |
|---|---|---|
| 26.2, 26.1.2, 26.1.1, 26.1 | resolves | the advertised range |
| 1.21.11 … 1.21.2 | resolves | the advertised range |
| 1.21.1, 1.21 | resolves | but `item_model` does not exist yet, so the mod falls back to `custom_model_data` — it works, and the ids you write are those values instead |
| 1.20.6, 1.20.5 | resolves | same `custom_model_data` fallback, plus a second copy of one mixin: `onDisconnect` takes a `Component` here and a `DisconnectionDetails` from 1.21, and a Mixin handler must mirror its target's parameters |
| 1.20.4 and earlier | **no** | no data components at all — `DataComponentType` and the registry the mod resolves through simply are not there. This is a floor, not a to-do |

Two deliberate choices keep that range on one source path, and both look like something to tidy up:

* **The component is looked up by id, not named.** `DataComponents.ITEM_MODEL` is a static field
  that exists only from 1.21.2. The registry has held component types since 1.20.5, so the type is
  fetched from it by id and a version that lacks it simply yields null. The registry is *iterated*
  rather than queried by key, because a key is an `Identifier` — see the next point.
* **The component's value is held as `Object`.** Its class is `Identifier` from 1.21.11 and
  `ResourceLocation` before it, and all the mod wants is `toString()`.

Naming either concrete type would halve the supported range for no benefit, so please don't.

The lookup is also **lazy**, not a static initialiser: a client mixin can load during bootstrap
while registries are still filling, and a lookup that ran then would cache a null and leave the mod
silently inert for the session.

Reproduce the table with:

```sh
python tools/verify_targets.py 26.2 1.21.8 1.20.6
```

The tool reads the targets out of the mixin sources, fetches each client jar from Mojang, and — for
anything before 26.1 — maps names through that version's `client_mappings`, because those jars are
obfuscated.

Building for a version is `./gradlew build -Pminecraft_version=1.21.8`; the default is the one in
`gradle.properties`.

### How much is actually verified, per version

Every supported version is launched and exercised automatically; the deeper gametest reaches only
part of the range:

| Minecraft | targets check | builds a jar | smoke test (real client) | client gametest |
|---|---|---|---|---|
| 26.2, 26.1.2, 26.1.1, 26.1 | yes | yes | **yes** | **yes** |
| 1.21.11 … 1.21.9 | yes | yes | **yes** | locally only — see below |
| 1.21.8 … 1.21.4 | yes | yes | **yes** | **yes** — all five |
| 1.21.3 … 1.20.5 | yes | yes | **yes** | **no** — the gametest API does not exist yet |

`./gradlew smokeTest -Pminecraft_version=1.21.2` starts a real client with the mod, waits for it to
render, and asserts the two things most likely to break when a version moves underneath the mod:
that the `item_model` component still resolves out of the registry, and that editing
`config/hidemodels.txt` still drives the matcher. It needs nothing but Fabric Loader, which is what
lets it run everywhere — including 26.x, where there is no Loom and `gradle/runclient.gradle` assembles
the launch by hand (client jar, loader's own libraries from `fabric-installer.json`, natives, and a
stub asset index so no gigabyte is downloaded to reach a title screen).

`./gradlew runClientGameTest -Pminecraft_version=1.21.8` goes further where Fabric's harness exists:
it builds a world, summons an `item_display` carrying `minecraft:item_model`, and asserts the mod
reads the component, intercepts its own command — typed *and* clicked — and honours its config. No
ModelEngine and no server are needed, because the mod keys on a vanilla component on a vanilla
entity, which is what makes it runnable anywhere.

**That includes 26.x, where there is no Loom.** The harness is published for 26.x like any other
version, and it is just a mod: `gradle/runclient.gradle` puts it in the run directory beside the mod
under test and launches the client by hand, so 26.x runs the identical test class. The harness never
needed Loom — only a launcher, which that file already was. The limit below 1.21.4 is real, though:
Fabric publishes no client gametest module there at all, checked against each version's own
`fabric-api` POM.

CI runs the smoke test on all eighteen versions and the gametest on nine of them — all four 26.x
releases and 1.21.4 through 1.21.8 — under xvfb.
What the smoke test cannot cover is anything needing a world: the render hook, the registered
command and `ChatOut` are only exercised where the gametest runs.

**The gametest does not run in CI on 1.21.9 and later.** On a hosted runner their integrated server
freezes at `Preparing spawn area: 16%` until the harness gives up with `Timeout loading world`. It
is not this mod, not the runner, and not Loom — each of those was tested rather than assumed:

* 1.21.4–1.21.8 and all four 26.x versions load a world on that same runner, and 26.x passes
  through that very `16%` line in under a second
* 1.21.11 loads in 21 seconds on a developer machine
* launching those three **without Loom**, through the production-mode launcher, fails on the runner
  in precisely the same way — so it is not the dev environment either

Also ruled out: render distance, per-frame cost, a frame-rate cap (which made it worse — the harness
steps client and server together, so a slower client is a slower server), llvmpipe's thread count,
the client pausing on lost focus, and Minecraft's background worker pool being one thread wide on a
two-core runner. What remains is something specific to 1.21.9+ world generation on a constrained
machine. Run them yourself with `./gradlew runClientGameTest -Pminecraft_version=1.21.11`; in CI the
smoke test keeps all three covered.

**One job runs the gametest the way an installed game runs it** — obfuscated jar, intermediary
mappings, remapped mods — because every other gametest job is a *development* launch, where the
runtime keeps official names. That difference hides bugs: it hid one here, where a check looked its
target up by name, found nothing against intermediary, and reported a code path as absent on a
version that has it.

Compiling is still what catches most version breaks — the checker is static and cannot see member
access, which is exactly how the real source differences below were found.

**Three small files differ across the range**, each on its own boundary, kept as pairs of copies
rather than behind a preprocessor. Notice that no two boundaries are in the same place — which is
why they are three separate splits rather than one "old versus new" fork:

| file | boundary | why |
|---|---|---|
| `ChatOut` (`src/mc121`, `src/mc26`) | 1.21.x ↔ 26.x | the client-facing chat call was renamed: `LocalPlayer.displayClientMessage` before, `sendSystemMessage` after |
| `ClickRun` (`src/click121`, `src/click1215`) | 1.21.4 ↔ 1.21.5 | `ClickEvent` was a class with a constructor, and became a sealed interface whose cases are records |
| `ClientDisconnectMixin` (`src/disconnect1206`, `src/disconnect121`) | 1.20.6 ↔ 1.21 | `onDisconnect` takes a `Component` before and a `DisconnectionDetails` after, and a Mixin handler must mirror its target's parameters |

Reflection cannot paper over any of them: a 1.21.x build is remapped to intermediary, so the runtime
name is something like `method_7353` and no name-based lookup would find it. For the chat call in
particular, `CommandSource.sendSystemMessage` does exist on every version — but on 1.21.x the player
does not override it, and the inherited server implementation prints nothing at all.

Three things the checker does not tell you. It is a **static** check: that a hook exists is not that it
fires. It reads only **declared** members, so an inherited one reads as absent. And it says nothing
about the **build** — 26.x ships readable jars and needs no mappings, which is why this project has
no Loom, while 1.21.x ships obfuscated ones and needs a remapping build even though the source is
identical.

## Configuring it

`config/hidemodels.txt`, one `item_model` id fragment per line, `#` for comments. Matching is a
**substring** test, so a trailing slash hides a whole model and no slash hides a single bone:

```
some_mount/         # the whole model
some_mount/head     # just the head, so you can see past it while riding
```

The shipped list is **empty**: which ids exist is entirely up to the server's resource pack, so any
preset would be wrong everywhere but one server.

**Directives**, each on a line of its own:

| line | effect |
|---|---|
| `off` | disable without emptying the list |
| `first-person-only` | hide only while the camera is in first person, so the model reappears in third person (F5) |
| `list-radius 32` | default radius for `/hidemodels list` |

`first-person-only` is the one to use for a mount whose head fills your screen: hidden while you're
riding and looking ahead, visible again the moment you pull the camera out to look at it. The
camera is read live on each render check, so pressing F5 takes effect on the next frame.

**When the config loads.** There is no entrypoint, so nothing happens at startup. The file is read
lazily from the render hook — the first time any `item_display` comes up for a render check. If it
doesn't exist by then, the mod writes a documented default and loads that. After that it's a
timestamp poll throttled to once a second, so a saved edit takes effect within about a second: no
restart, no command, no keybind. An unreadable file keeps the previous list rather than taking the
renderer down with it.

## The `/hidemodels` command

| command | what it does |
|---|---|
| `/hidemodels` | status: pattern count, on/off, first-person and server-opt-out state |
| `/hidemodels list [radius]` | every model within the radius, grouped by model, nearest first |
| `/hidemodels list bones [radius]` | the same, but individual bone ids |
| `/hidemodels add <id>` | hide it now — writes the line and reloads |
| `/hidemodels remove <id>` | stop hiding it |
| `/hidemodels on` / `off` | stop hiding without emptying the list |
| `/hidemodels first-person on` / `off` | hide only while the camera is in first person |
| `/hidemodels radius <blocks>` | the default radius for `list` |

**Every setting is also a command, and every command edits the config.** The three directives below
can be typed into the file or set in game, and both routes write the same file and reload it — so
there is no second place where state could live and disagree.

Each row shows the piece count, the id, and the distance to the nearest piece; ids your config
already hides are green and marked `hidden`. Radius defaults to `list-radius` in the config (32)
and is clamped to 256. Output is capped at 40 rows.

```
hidemodels: 3 models within 32 blocks (12 pieces)
  x8  modelengine:some_mount/     0.9m  hidden
  x2  modelengine:internal_fire/  0.9m  click to hide
  x2  modelengine:warp_core/      14.3m  click to hide
```

**The ids are clickable.** Anything not already hidden is underlined and runs `add` for you, so the
whole loop is: stand next to the thing, run `list`, click it, watch it vanish. Ids that are already
hidden are not clickable — a click that did nothing would be worse than none, and removal stays a
typed command on purpose, so nothing disappears from your config by a stray click in chat.

`add` appends to the config rather than rewriting it, leaving your comments and directives where
you put them, and reloads immediately instead of waiting for the poll. It tells you when an id is
already covered by a broader line rather than silently adding a redundant one. `remove` deletes a
line that matches exactly; if the id is only hidden because of a broader pattern, it says which one
rather than deleting more than you asked.

The command is handled entirely on the client and is **not** forwarded to the server. It also isn't
registered in the command tree, so it won't tab-complete — vanilla builds that tree from what the
server advertises, and adding a local command properly would mean depending on Fabric API, which
this mod deliberately avoids.

## Finding ids without the command

Every piece of a ModelEngine model is an `item_display` whose item carries an `item_model`
component. Since 1.21.4 those ids resolve to item definition files inside the server's resource
pack:

```
modelengine:some_mount/head  ->  assets/modelengine/items/some_mount/head.json
```

So the complete list of ids a server can ever show you is in the pack your client already
downloaded, under `<gamedir>/downloads` (the file has no extension — `downloads/log.json` maps each
name back to its original URL). Unzip it and list `assets/*/items/**`.

Be specific with fragments: scenery, props and interactive models are `item_display`s too, so a
too-broad pattern will hide things you still want to see, such as teleporters or signposts.

## For server operators: turning it off

A server can switch this mod off for its own players. Send an **empty custom payload** on:

| channel | effect |
|---|---|
| `hidemodels:disable` | hiding is off for the rest of the session |
| `hidemodels:enable` | hiding is allowed again |

The channel *is* the message — there's no body to parse, because an unknown plugin channel reaches
the client as a `DiscardedPayload`, which in 26.2 keeps only the channel id and throws the bytes
away before any mod can read them.

Sending it is safe unconditionally: clients without this mod discard unknown channels silently, so
you can fire it at every player on join without checking who has what installed. The override
lasts for one connection — it's cleared on disconnect, so a server can't leave a client
permanently altered, and the player's own config applies again next time they connect elsewhere.

## Building

```sh
export JAVA_HOME=/path/to/jdk-25
./gradlew build          # -> build/libs/hidemodels-1.5.0+mc26.2.jar
```

No local Minecraft install is needed: the compile classpath — client jar plus MC's own libraries —
is fetched from Mojang's piston metadata and sha1-verified, then cached under
`build/minecraft/26.2/`. See [BUILDING.md](BUILDING.md) for why there is no Fabric Loom here, and
for the two 26.2 API details that cost the most time.

Prebuilt jars come from GitHub Actions: the artifact on every build, and a Release for every `v*`
tag.

## License

**LGPL-3.0-only.** The full text is in [LICENSE](LICENSE); LGPLv3 is written as a set of additional
permissions on top of GPLv3, so that text is included as [COPYING](COPYING) too, as the licence
itself requires.

In practice: use it, ship it in a modpack, fork it — but if you distribute a modified version of
*this* code, those modifications stay open under the same licence. A separate mod that merely
depends on this one is unaffected, which is the difference between LGPL and GPL and the reason this
is the common choice for Minecraft mods (Iris, Lithium and Sodium Extra all use it).

Releases up to and including **1.4.2 were published under MIT** and remain so; the change applies
from 1.5.0 onward.

Two things this licence does not cover, because they are not mine to license: the mod's icon is a
screenshot of Minecraft containing Mojang's assets and a third-party ModelEngine model, and nothing
here grants any right to Minecraft itself.
