# Hide Models

A client-side Fabric mod for **Minecraft 1.21.2 – 26.2** that stops chosen [ModelEngine](https://mythiccraft.io/index.php?resources/modelengine.1/)
model pieces from rendering — a mount's head that blocks your view, a pet that follows you around,
a visual effect you'd rather not see — chosen by their `item_model` id.

It **cancels rendering only**. Entities are never removed, so hitboxes, interactions and the
server's view of the world are all untouched.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.0 or newer.
2. Drop the jar **for your Minecraft version** into `mods/` — they are named
   `hidemodels-1.5.0+mc26.2.jar`, `hidemodels-1.5.0+mc1.21.8.jar` and so on. Loader refuses to load
   the wrong one rather than failing later.

Requirements, all declared in `fabric.mod.json`:

| | |
|---|---|
| Minecraft | 1.21.x jars: exactly the version named, e.g. `=1.21.8`. 26.x jars: that version and its patch releases, e.g. `~26.1` covers 26.1.1 and 26.1.2 — see below |
| Fabric Loader | `>=0.19.0` |
| Java | `>=25` on 26.x, `>=21` on 1.21.x — each version's own requirement |
| Fabric API | **not needed** |
| Other mods | none |

Client-only (`"environment": "client"`), so there is nothing to install server-side.

**Why 1.21.x pins exactly and 26.x does not.** Those two paths are built differently, so they are
compatible at different granularities. A 1.21.x jar is remapped to that version's intermediary
names, and a mixin whose target moved does not fail loudly — it simply never applies, so the mod
would hide nothing with nothing in the log to say why. Pinning exactly makes Loader refuse that jar
up front instead, and there is a jar for every Minecraft release in the range, so nothing is left
uncovered. A 26.x jar is never remapped at all — the client jar is already in readable names, which
is why this project has no Loom — so it is genuinely compatible across a minor's patch releases, and
says so.

## Which versions it can target

**1.21.2 through 26.2**, from one source tree with no preprocessor and no per-version source sets —
every mixin target and every vanilla type the mod names resolves on all of them, checked against
each version's own client jar rather than assumed. CI runs that matrix on every push.

| Minecraft | source | notes |
|---|---|---|
| 26.2, 26.1.2, 26.1.1, 26.1 | resolves | the advertised range |
| 1.21.11 … 1.21.2 | resolves | the advertised range |
| 1.21.1 | resolves | but `item_model` does not exist, so it falls back to `custom_model_data` — the mod runs, and the ids you would write are different |
| 1.20.6 | **one break** | `onDisconnect` takes `Component` there, `DisconnectionDetails` after — a Mixin handler must mirror its target's parameters, so this one needs a real branch |
| 1.20.4 and earlier | **no** | no data components at all |

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
| 26.2, 26.1.2, 26.1.1, 26.1 | yes | yes | **yes** | **no** — Fabric ships no gametest module for 26.x |
| 1.21.11 … 1.21.9 | yes | yes | **yes** | locally only — see below |
| 1.21.8 … 1.21.4 | yes | yes | **yes** | **yes** — all five |
| 1.21.3, 1.21.2 | yes | yes | **yes** | **no** — the gametest API does not exist yet |

`./gradlew smokeTest -Pminecraft_version=1.21.2` starts a real client with the mod, waits for it to
render, and asserts the two things most likely to break when a version moves underneath the mod:
that the `item_model` component still resolves out of the registry, and that editing
`config/hidemodels.txt` still drives the matcher. It needs nothing but Fabric Loader, which is what
lets it run everywhere — including 26.x, where there is no Loom and `gradle/run26.gradle` assembles
the launch by hand (client jar, loader's own libraries from `fabric-installer.json`, natives, and a
stub asset index so no gigabyte is downloaded to reach a title screen).

`./gradlew runClientGameTest -Pminecraft_version=1.21.8` goes further where Fabric's harness exists:
it builds a world, summons an `item_display` carrying `minecraft:item_model`, and asserts the mod
reads the component, intercepts its own command, and honours its config. No ModelEngine and no
server are needed, because the mod keys on a vanilla component on a vanilla entity — which is what
makes it runnable anywhere.

CI runs the smoke test on all fourteen versions and the gametest on 1.21.4 through 1.21.8, under xvfb.
What the smoke test cannot cover is anything needing a world: the render hook, the command mixin
and `ChatOut` are only exercised where the gametest runs.

**The gametest does not run in CI on 1.21.9 and later.** On a hosted runner their integrated server
freezes at `Preparing spawn area: 16%` — the identical percentage, logged minute after minute —
until the harness gives up with `Timeout loading world`. It is not this mod and it is not mere
slowness: the same commit passes on 1.21.4 and 1.21.8 on the same runner, and passes on 1.21.11 on
a developer machine in 21 seconds. Ruled out by experiment: render distance, per-frame cost, a
frame-rate cap (which made it worse — the harness steps client and server together, so a slower
client is a slower server), llvmpipe's thread count, the client pausing on lost focus, and
Minecraft's background worker pool being one thread wide on a two-core runner. Run it yourself on
those versions with `./gradlew runClientGameTest -Pminecraft_version=1.21.11`; the smoke test keeps
them covered in CI.

Compiling is still what catches most version breaks — the checker is static and cannot see member
access, which is exactly how the one real source difference below was found.

**One file differs across the range**, `ChatOut`, kept as two small copies under `src/mc26` and
`src/mc121` rather than behind a preprocessor. The client-facing chat call was renamed —
`LocalPlayer.displayClientMessage` up to 1.21.x, `sendSystemMessage` from 26.x — and reflection
cannot bridge it, because a 1.21.x build is remapped to intermediary and the runtime name is
`method_7353`. `CommandSource.sendSystemMessage` does exist on every version, but on 1.21.x the
player does not override it, so the inherited server implementation would print nothing at all.

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

MIT.
