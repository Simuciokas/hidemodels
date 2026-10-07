# Hide Models

A client-side Fabric mod for **Minecraft 1.20.5 – 26.3** that stops chosen server-side model pieces
from rendering — a mount's head that blocks your view, a pet that follows you around, a visual
effect you'd rather not see — chosen by their `item_model` id.

It **cancels rendering only**. Entities are never removed, so hitboxes, interactions and the
server's view of the world are all untouched.

**It keys on the vanilla mechanism, not on any one plugin.** A model piece is an `item_display`
entity, or an armor stand wearing the piece on its head, whose item carries `item_model` (or
`custom_model_data` before 1.21.2). That is how [ModelEngine](https://mythiccraft.io/index.php?resources/modelengine.1/),
[BetterModel](https://modrinth.com/plugin/bettermodel), [Nexo](https://docs.nexomc.com) and
[Oraxen](https://docs.oraxen.com/creating-content/furniture/display-entities) furniture all render,
so all of them work without the mod knowing they exist — and so does anything else built the same
way. What is tested is the *shape*, not the plugin: the client gametest builds both entity forms and
asserts each one resolves to its id and is hidden by the config.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.0 or newer, or NeoForge.
2. Drop the jar **covering your Minecraft version** into `mods/`. Each jar names the range it
   covers — `hidemodels-2.0.1+mc26.1-26.3-fabric.jar`, `hidemodels-2.0.1+mc1.20.5-1.21.11-fabric.jar`
   — and declares that range, so Loader refuses the wrong one rather than failing later.

**Why a range rather than one jar per version.** Across each range the compiled mod is
byte-identical: the same classes, the same mixins, differing only in the metadata that names the
range. Shipping four files instead of thirty-one is therefore not a compromise, it is the truth about
what was built — and a CI job rebuilds every version on every push and fails if any of them stops
matching the group whose jar it would ship under.

Requirements, all declared in `fabric.mod.json`:

| | |
|---|---|
| Minecraft | the range the jar names, e.g. `>=1.20.5 <=1.21.11`. Bounded at both ends: it claims nothing it was not built against |
| Fabric Loader | `>=0.19.0` — or Quilt Loader, which runs the same jar |
| Java | `>=25` on 26.x, `>=21` on 1.21.x — each version's own requirement |
| Fabric API | **required** on Fabric and Quilt. Not on NeoForge, where its work is done by NeoForge's own events |
| Other mods | none; with Mod Menu installed, its Configure button opens the screen |

Client-only (`"environment": "client"`), so there is nothing to install server-side.

**Why the range is bounded at both ends.** An open `>=1.20.5` would claim versions this jar has
never been built or tested against, and the failure that produces is the worst kind: a 1.21.x jar is
remapped to one version's intermediary names, and a mixin whose target moved does not fail loudly —
it simply never applies, so the mod hides nothing with nothing in the log to explain it. Every jar
therefore states exactly the span it was built and tested across, and Loader refuses anything
outside it.

## Which versions it can target

**1.20.5 through 26.3** — nineteen releases — from one source tree with no preprocessor, and with
a handful of small files that differ between 1.21.x and 26.x —
every mixin target and every vanilla type the mod names resolves on all of them, checked against
each version's own client jar rather than assumed. CI runs that matrix on every push.

| Minecraft | source | notes |
|---|---|---|
| 26.3, 26.2, 26.1.2, 26.1.1, 26.1 | resolves | the advertised range |
| 1.21.11 … 1.21.2 | resolves | the advertised range |
| 1.21.1, 1.21 | resolves | but `item_model` does not exist yet, so the mod falls back to `custom_model_data` — it works, and the ids you write are those values instead |
| 1.20.6, 1.20.5 | resolves | same `custom_model_data` fallback |
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
python tools/verify_targets.py 26.3 1.21.8 1.20.6
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
| 26.3, 26.2, 26.1.2, 26.1.1, 26.1 | yes | yes | **yes** | **yes** |
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

**The Configure button needs Mod Menu to test.** The entrypoint is compiled against a copy of Mod
Menu's two interfaces in `src/stubs`, which the jar leaves out, so no Mod Menu build has to be picked
per Minecraft version. A launch that assembles `mods/` itself takes `-PextraMods=modmenu.jar`, and
the smoke test then asks Mod Menu for the screen the way its button does. On NeoForge it always
asks the mods list's factory.

`./gradlew runClientGameTest -Pminecraft_version=1.21.8` goes further where Fabric's harness exists:
it builds a world, summons an `item_display` carrying `minecraft:item_model`, and asserts the mod
reads the component, honours its config, and that `/hidemodels`, its key and every tab of the screen
do what they say. No ModelEngine and no server are needed, because the mod keys on a vanilla
component on a vanilla entity, which is what makes it runnable anywhere.

**That includes 26.x, where there is no Loom.** The harness is published for 26.x like any other
version, and it is just a mod: `gradle/runclient.gradle` puts it in the run directory beside the mod
under test and launches the client by hand, so 26.x runs the identical test class. The harness never
needed Loom — only a launcher, which that file already was. The limit below 1.21.4 is real, though:
Fabric publishes no client gametest module there at all, checked against each version's own
`fabric-api` POM.

Both launch a real client, which makes them slow. CI has a job for each, under xvfb, but runs them
only when the repository variable `RUN_CLIENT_TESTS` is `true`; otherwise they are run locally
before each release, and CI keeps the targets check, the compile of every version and the group
check. What the smoke test cannot cover is anything needing a world: the render hook, the screen, the
command and `ChatOut` are only exercised where the gametest runs.

**Even when CI runs them, the gametest skips 1.21.9 and later.** On a hosted runner their integrated server
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

**A few small files differ across the range**, kept as pairs of copies rather than behind a
preprocessor, and all of them split where the jars already do, between 1.21.x and 26.x:

| files | why |
|---|---|
| `ChatOut` (`src/versions/chat-mc121`, `chat-mc26`) | the client-facing chat calls were renamed: `LocalPlayer.displayClientMessage` before, `sendSystemMessage` and, over the hotbar, `sendOverlayMessage` after |
| `ClearScreen`, `Painter`, `Screens` (`src/versions/screen-mc121`, `screen-mc26`) | 26.x replaced `GuiGraphics` and the screen's render methods, and `setScreen` with `setScreenAndShow` |
| `Cmd`, `KeyRegistration` (`src/versions/fabricapi-mc121`, `fabricapi-mc26`) | Fabric API's renames, not Minecraft's: `ClientCommandManager` became `ClientCommands`, and `KeyBindingHelper` became `KeyMappingHelper` |

Those cost nothing, since 26.x needs Java 25 class files and gets its own jar regardless. Changes
*inside* a jar's range are reached by reflection instead, and every candidate name is checked against
each version by `tools/verify_targets.py`, because a 1.21.x build runs against intermediary names
like `method_7353` that no compiler sees.

Three things the checker does not tell you. It is a **static** check: that a hook exists is not that it
fires. It reads only **declared** members, so an inherited one reads as absent. And it says nothing
about the **build** — 26.x ships readable jars and needs no mappings, which is why this project has
no Loom, while 1.21.x ships obfuscated ones and needs a remapping build even though the source is
identical.

## Configuring it

`config/hidemodels.txt`, one `item_model` id fragment per line, and `#` at the start of a line for a
comment — after an id it would be read as part of it. Matching is a **substring** test, so a
trailing slash hides a whole model and no slash hides a single bone:

```
# the whole model
some_mount/
# just the head, so you can see past it while riding
some_mount/head
```

The shipped list is **empty**: which ids exist is entirely up to the server's resource pack, so any
preset would be wrong everywhere but one server.

**Directives**, each on a line of its own:

| line | effect |
|---|---|
| `off` | disable without emptying the list |
| `first-person-only` | hide only while the camera is in first person, so the model reappears in third person (F5) |
| `list-radius 32` | how far the screen's Nearby tab looks, in blocks |
| `gui-position 1 0` | where the screen sits, as fractions of the free space: `0 0` is the top left (the default), `1 0` the top right, `1 1` the bottom right |

`first-person-only` is the one to use for a mount whose head fills your screen: hidden while you're
riding and looking ahead, visible again the moment you pull the camera out to look at it. The
camera is read live on each render check, so pressing F5 takes effect on the next frame.

**Profiles** are saved lists in the same file. A line in square brackets starts one, and the lines
under it, up to the next heading, apply while it is on; `off` after the brackets keeps it saved but
not applied. The lines above the first heading always apply — the screen calls them unsaved:

```
# unsaved: always applies
some_mount/head

[Mounts]
modelengine:some_mount/
[PvP] off
modelengine:wings/
```

Any number can be on at once, and a model is hidden if any of them hides it. A name is anything
without a square bracket, and two names that differ only in case are the same profile. Directives
belong to the whole file wherever they are written; the screen writes them above the first heading.

**Profiles cost nothing per frame.** When the file loads, the unsaved lines and every profile that is
on are merged into the one list the render check already reads, so the render hook never sees a
profile at all, and switching one is a reload rather than a lookup.

**When the config loads.** There is no entrypoint, so nothing happens at startup. The file is read
lazily from the render hook — the first time any `item_display` comes up for a render check. If it
doesn't exist by then, the mod writes a documented default and loads that. After that it's a
timestamp poll throttled to once a second, so a saved edit takes effect within about a second: no
restart and nothing to click. An unreadable file keeps the previous list rather than taking the
renderer down with it.

## In game

`/hidemodels` opens the screen, and so does a key: unbound until you pick one, under Hide Models
in Controls. There is nothing else to type. **Shift with that key** switches hiding on or off
without opening anything, and says which over the hotbar. Controls cannot record a combination on
Fabric, so the mod reads Shift itself: bind the key to H, and Shift+H is the switch. The mods list
opens the screen too: Mod Menu's Configure button on Fabric and Quilt, Config on NeoForge.

The screen is a panel in the top left, leaving the rest of the view clear so you can watch a model
go as you click it. It has three tabs:

| tab | what it holds |
|---|---|
| Nearby | every model within the list radius: the one in front of you first, marked `»`, then nearest first; click one to hide it, click again to bring it back. The `▶` beside a model lists its bones, to hide a single piece such as a mount's head; a model with some bones hidden is marked orange, and clicking it hides the rest, then shows all of it again. A model a profile hides opens that profile instead |
| Hidden | your lists: Unsaved, then each profile. Click a profile to switch it on or off, its count to open it, and `+` beside it to add the unsaved lines to it. In an open list a click takes a line out and keeps its row until the list is opened again, so a misclick is one click to undo; an open profile is also where it is renamed, by typing into its name, and deleted |
| Settings | hiding on or off, first person only, the list radius, and where the panel sits: either top corner, or Move panel to put it anywhere |

Hovering over a row says more: a model's piece count and how far away it is, how many of its pieces
single-bone lines hide, and which line or profile hides it. When that line is broader than the
model's own id, clicking the row cannot remove it, and the box says to unhide it from the Hidden tab
instead.

**Profiles** keep sets of hidden models you switch between, rather than one list you keep editing.
What you hide by clicking goes into Unsaved, which always applies; *Save unsaved as a profile*
moves those lines into a new profile, switched on, so nothing changes on screen until you switch it
off. Switching a profile off brings its models back without forgetting them. A profile only changes
from inside it, which is why clicking one of its models in Nearby opens it: a click that quietly
edited a saved list would be easy to make and hard to notice.

**Everything the screen changes is a line in the config.** Hiding a model appends its id to the
unsaved lines, unhiding removes that exact line, a profile is its heading and the lines under it,
and each setting is one of the directives above, so the file stays the one description of what the
mod is doing and editing it by hand works just as well. Appending leaves your comments and
directives where you put them, and a change from the screen applies at once rather than waiting for
the poll.

The command is handled entirely on the client and is **not** forwarded to the server.

## Finding ids by hand

Every piece of a model is an `item_display` — or an armor stand wearing it as a helmet — whose item
carries an `item_model` component. Since 1.21.4 those ids resolve to item definition files inside
the server's resource pack:

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
./gradlew build          # -> build/libs/hidemodels-2.0.1+mc26.1-26.3-fabric.jar
```

No local Minecraft install is needed: the compile classpath — client jar plus MC's own libraries —
is fetched from Mojang's piston metadata and sha1-verified, then cached under
`build/minecraft/26.3/`. See [BUILDING.md](BUILDING.md) for why there is no Fabric Loom here, and
for the two 26.2 API details that cost the most time.

Prebuilt jars come from GitHub Actions: the artifact on every build, and a Release for every `v*`
tag.

To try it without a server, `./gradlew runDemo -Pminecraft_version=26.3` starts an offline client
that places a few sample models in front of you whenever you join a single-player world (26.x only;
there are no sounds, as the launch skips the asset download).

**Chat stays quiet.** The screen shows every change it makes, so the mod writes to chat only to say
why a click changed nothing. A run with `-Dhidemodels.debug=true` — or `runDemo -Pdebug` — also
confirms each change there.

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
