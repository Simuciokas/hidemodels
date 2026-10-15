#!/usr/bin/env python3
"""
A vanilla server for the in-world smoke run.

  python tools/smoke_server.py <minecraft version> --java <java executable> [--port 25599]

then, once it prints READY, a smoke run given -PsmokeWorld=127.0.0.1:<port> joins it at launch and
SmokeChecks checks the fade hooks in its world.

WHY A SERVER. The smoke runs have no world of their own - Fabric's client gametest makes one, but
only on Fabric. A server of the same Minecraft version works for every loader alike, the client
joins it with vanilla's own --quickPlayMultiplayer, and its console places the models: no data pack,
whose format changes from version to version, and no operator rights for the test player.

It downloads that version's server jar from Mojang into build/smoke-server/<version>/, keeps it for
the next run, and starts on a fresh flat world. When the player joins, it puts in front of them an
item display and an armor stand wearing a piece on its head - by item_model from 1.21.2, by a
custom_model_data number before. It stops the server and exits when the player leaves, or after
--timeout seconds.
"""
import argparse
import http.client
import json
import pathlib
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
UA = {"User-Agent": "hidemodels-smoke-server"}


def fetch(url, tries=3):
    """A cut-off download is tried again: Mojang's CDN drops the odd one."""
    for attempt in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
                return r.read()
        except (OSError, http.client.IncompleteRead):
            if attempt == tries - 1:
                raise
            time.sleep(3)


def server_jar(version, home):
    jar = home / "server.jar"
    if not jar.exists():
        manifest = json.loads(fetch(MANIFEST))
        entry = next((v for v in manifest["versions"] if v["id"] == version), None)
        if entry is None:
            sys.exit(f"Mojang lists no version {version}")
        meta = json.loads(fetch(entry["url"]))
        jar.write_bytes(fetch(meta["downloads"]["server"]["url"]))
    return jar


def has_item_model(version):
    """item_model arrived in 1.21.2; 26.x has it."""
    parts = [int(p) for p in version.split(".")]
    return parts[0] >= 26 or parts[1:] >= [21, 2]


def models(version):
    """The console commands that put the two models in front of the player."""
    if has_item_model(version):
        display = '"minecraft:item_model":"hidemodels:smoke/display"'
        stand = 'minecraft:item_model="hidemodels:smoke/stand"'
    else:
        display = '"minecraft:custom_model_data":1234'
        stand = "minecraft:custom_model_data=1234"
    here = "execute at @a[limit=1] rotated as @a[limit=1] run "
    return [
        here + "summon item_display ^ ^1.6 ^3 {Tags:[\"hm_smoke\"],"
               "item:{id:\"minecraft:stone\",count:1,components:{" + display + "}}}",
        here + "summon armor_stand ^1.5 ^ ^3 {Tags:[\"hm_smoke\",\"hm_smoke_stand\"]}",
        "item replace entity @e[tag=hm_smoke_stand,limit=1] armor.head with minecraft:stone["
        + stand + "]",
    ]


def main():
    args = argparse.ArgumentParser()
    args.add_argument("version")
    args.add_argument("--java", required=True)
    args.add_argument("--port", type=int, default=25599)
    args.add_argument("--timeout", type=int, default=600)
    opts = args.parse_args()

    home = ROOT / "build" / "smoke-server" / opts.version
    home.mkdir(parents=True, exist_ok=True)
    jar = server_jar(opts.version, home)
    shutil.rmtree(home / "world", ignore_errors=True)
    (home / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    layers = json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1},
                                    {"block": "minecraft:stone", "height": 3},
                                    {"block": "minecraft:grass_block", "height": 1}],
                         "biome": "minecraft:plains"}, separators=(",", ":"))
    (home / "server.properties").write_text("\n".join([
        "online-mode=false", "white-list=false", "enforce-secure-profile=false",
        "server-ip=127.0.0.1", f"server-port={opts.port}",
        "level-type=minecraft\\:flat", f"generator-settings={layers}",
        "generate-structures=false", "difficulty=peaceful", "spawn-protection=0",
        "gamemode=creative", "view-distance=4", "simulation-distance=4", "max-players=2",
        "motd=Hide Models smoke server",
    ]) + "\n", encoding="utf-8")

    server = subprocess.Popen([opts.java, "-Xmx1G", "-jar", jar.name, "nogui"], cwd=home,
                              stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True, encoding="utf-8",
                              errors="replace")

    def send(command):
        server.stdin.write(command + "\n")
        server.stdin.flush()

    def stop():
        if server.poll() is None:
            send("stop")
            try:
                server.wait(60)
            except subprocess.TimeoutExpired:
                server.kill()

    timer = threading.Timer(opts.timeout, stop)
    timer.daemon = True
    timer.start()
    for line in server.stdout:
        line = line.rstrip()
        if "Done (" in line:
            print("READY", flush=True)
        # 26.3 logs these as "System chat: <name> joined the game".
        elif re.search(r"\]: (System chat: )?\S+ joined the game", line):
            print(line, flush=True)
            time.sleep(2)
            for command in models(opts.version):
                send(command)
        elif re.search(r"\]: (System chat: )?\S+ left the game", line):
            print(line, flush=True)
            stop()
        elif "ERROR" in line or "Exception" in line or "Summoned" in line or "Replaced" in line:
            print(line, flush=True)
    timer.cancel()
    return server.wait()


if __name__ == "__main__":
    sys.exit(main())
