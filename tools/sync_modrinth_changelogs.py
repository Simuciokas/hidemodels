#!/usr/bin/env python3
"""
Push docs/releases/<tag>.md onto the Modrinth versions it belongs to.

Modrinth reads the changelog once, when a version is published, so a note edited afterwards
only reaches the download page if something sends it. This sends it.

Every Modrinth version carries its mod version in its name - "mc26.1-26.3-1.8.0-fabric" - so the
file to use is derived rather than passed: the trailing x.y.z before the loader suffix.

  MODRINTH_TOKEN=... python tools/sync_modrinth_changelogs.py <project id>          # dry run
  MODRINTH_TOKEN=... APPLY=1 python tools/sync_modrinth_changelogs.py <project id>
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request

API = "https://api.modrinth.com/v2"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# The loader suffix anchors the match: several of the Minecraft ranges in a version name are
# themselves x.y.z, so "the last one" is the only unambiguous rule.
VERSION = re.compile(r"-(\d+\.\d+\.\d+)-(?:fabric|neoforge|quilt)$")


def request(method, url, token, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", token)
    if data:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=60) as r:
        body = r.read()
        return json.loads(body) if body else None


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: sync_modrinth_changelogs.py <project id>")
    token = os.environ.get("MODRINTH_TOKEN")
    if not token:
        sys.exit("MODRINTH_TOKEN is not set")
    apply = os.environ.get("APPLY") == "1"

    versions = request("GET", "%s/project/%s/version" % (API, sys.argv[1]), token)
    changed = skipped = 0
    for v in versions:
        m = VERSION.search(v["version_number"])
        if not m:
            print("  ?? %-34s no mod version in the name" % v["version_number"])
            skipped += 1
            continue
        note = os.path.join(ROOT, "docs", "releases", "v%s.md" % m.group(1))
        if not os.path.isfile(note):
            print("  -- %-34s no %s" % (v["version_number"], os.path.basename(note)))
            skipped += 1
            continue
        want = open(note, encoding="utf-8").read().strip()
        if (v.get("changelog") or "").strip() == want:
            print("  == %-34s already current" % v["version_number"])
            continue
        print("  -> %-34s %d words -> %d"
              % (v["version_number"], len((v.get("changelog") or "").split()), len(want.split())))
        if apply:
            request("PATCH", "%s/version/%s" % (API, v["id"]), token, {"changelog": want})
        changed += 1

    print("\n%d to change, %d skipped%s" % (changed, skipped, "" if apply else "  (dry run - set APPLY=1)"))


if __name__ == "__main__":
    main()
