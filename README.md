# Hide Models

A client-side Fabric mod for **Minecraft 26.2** that stops chosen [ModelEngine](https://mythiccraft.io/index.php?resources/modelengine.1/)
model pieces from rendering — a mount's head that blocks your view, a pet that follows you around,
a visual effect you'd rather not see — chosen by their `item_model` id.

It **cancels rendering only**. Entities are never removed, so hitboxes, interactions and the
server's view of the world are all untouched.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.0 or newer for Minecraft 26.2.
2. Drop `hidemodels-1.3.0.jar` into your `mods/` folder.

Requirements, all declared in `fabric.mod.json`:

| | |
|---|---|
| Minecraft | `~26.2` (Loader refuses to load it on anything else) |
| Fabric Loader | `>=0.19.0` |
| Java | `>=25` — 26.2's own requirement, its client jar is class-file major 69 |
| Fabric API | **not needed** |
| Other mods | none |

Client-only (`"environment": "client"`), so there is nothing to install server-side.

## Which versions it can target

**1.21.2 through 26.2**, from one source tree — every mixin target and every vanilla type the mod
names resolves on all of them, checked against each version's own client jar rather than assumed:

| Minecraft | verdict |
|---|---|
| 26.2, 26.1 | every target resolves |
| 1.21.11 … 1.21.2 | every target resolves |
| 1.21.1 and earlier | **`minecraft:item_model` is absent** |

That component is the floor and it is a hard one: the mod identifies models by it, so an earlier
version would need a different key (CustomModelData), which is a different feature rather than a
port. The render hook itself goes back much further — `EntityRenderDispatcher.shouldRender` and
`Display.ItemDisplay` exist unbroken to 1.19.4 — so it is only the identification that stops.

Keeping one source tree across that range costs exactly one deliberate choice, in
`EntityRenderDispatcherMixin` and `NearbyModels`: the value of the `item_model` component is held
as `Object`, never as its concrete type. That type is `Identifier` from 1.21.11 and
`ResourceLocation` before it, and the only thing the mod wants from it is `toString()`. Naming
either would pin the source to half the range for no benefit — so please do not "tidy" it back to
the concrete type.

Reproduce the table with:

```sh
python tools/verify_targets.py 26.2 1.21.8 1.21.1
```

The tool reads the targets out of the mixin sources, fetches each client jar from Mojang, and — for
anything before 26.1 — maps names through that version's `client_mappings`, because those jars are
obfuscated. CI runs it over the whole supported range on every push.

Two things it deliberately does not tell you. It is a static check: that a hook exists is not that
it fires. And it says nothing about the BUILD — 26.x ships readable jars and needs no mappings,
while 1.21.x ships obfuscated ones, so producing a jar for those versions needs a remapping build
even though the source is identical.

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

Each row shows the piece count, the id, and the distance to the nearest piece; ids your config
already hides are green and marked `hidden`. Radius defaults to `list-radius` in the config (32)
and is clamped to 256. Output is capped at 40 rows.

```
hidemodels: 3 models within 32 blocks (12 pieces)
  x8  modelengine:some_mount/     0.9m  hidden
  x2  modelengine:internal_fire/  0.9m
  x2  modelengine:warp_core/      14.3m
```

This is the fast way to fill in the config: stand next to the thing, run the command, copy the id.

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
./gradlew build          # -> build/libs/hidemodels-1.3.0.jar
```

No local Minecraft install is needed: the compile classpath — client jar plus MC's own libraries —
is fetched from Mojang's piston metadata and sha1-verified, then cached under
`build/minecraft/26.2/`. See [BUILDING.md](BUILDING.md) for why there is no Fabric Loom here, and
for the two 26.2 API details that cost the most time.

Prebuilt jars come from GitHub Actions: the artifact on every build, and a Release for every `v*`
tag.

## License

MIT.
