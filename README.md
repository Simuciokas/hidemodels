# Hide Models

A client-side Fabric mod for **Minecraft 26.2** that stops chosen [ModelEngine](https://mythiccraft.io/index.php?resources/modelengine.1/)
model pieces from rendering — a mount's head that blocks your view, a pet that follows you around,
a visual effect you'd rather not see — chosen by their `item_model` id.

It **cancels rendering only**. Entities are never removed, so hitboxes, interactions and the
server's view of the world are all untouched.

> Status: builds cleanly and the hooks are correct, but it has not yet been runtime-tested in a
> live session. Treat 1.0.0 as unproven.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.0 or newer for Minecraft 26.2.
2. Drop `hidemodels-1.0.0.jar` into your `mods/` folder.

Requirements, all declared in `fabric.mod.json`:

| | |
|---|---|
| Minecraft | `~26.2` (Loader refuses to load it on anything else) |
| Fabric Loader | `>=0.19.0` |
| Java | `>=25` — 26.2's own requirement, its client jar is class-file major 69 |
| Fabric API | **not needed** |
| Other mods | none |

Client-only (`"environment": "client"`), so there is nothing to install server-side.

## Configuring it

`config/hidemodels.txt`, one `item_model` id fragment per line, `#` for comments. Matching is a
**substring** test, so a trailing slash hides a whole model and no slash hides a single bone:

```
some_mount/         # the whole model
some_mount/head     # just the head, so you can see past it while riding
```

The shipped list is **empty**: which ids exist is entirely up to the server's resource pack, so any
preset would be wrong everywhere but one server.

Put `off` on a line by itself to disable without emptying the list.

**When the config loads.** There is no entrypoint, so nothing happens at startup. The file is read
lazily from the render hook — the first time any `item_display` comes up for a render check. If it
doesn't exist by then, the mod writes a documented default and loads that. After that it's a
timestamp poll throttled to once a second, so a saved edit takes effect within about a second: no
restart, no command, no keybind. An unreadable file keeps the previous list rather than taking the
renderer down with it.

## Finding ids to put in it

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

## Building

```sh
export JAVA_HOME=/path/to/jdk-25
./gradlew build          # -> build/libs/hidemodels-1.0.0.jar
```

No local Minecraft install is needed: the compile classpath — client jar plus MC's own libraries —
is fetched from Mojang's piston metadata and sha1-verified, then cached under
`build/minecraft/26.2/`. See [BUILDING.md](BUILDING.md) for why there is no Fabric Loom here, and
for the two 26.2 API details that cost the most time.

Prebuilt jars come from GitHub Actions: the artifact on every build, and a Release for every `v*`
tag.

## License

MIT.
