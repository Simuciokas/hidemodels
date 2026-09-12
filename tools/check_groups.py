#!/usr/bin/env python3
"""
Do the versions in each release group really share a jar?

THE POINT. The build ships one jar per GROUP rather than per Minecraft version, which is only
honest while every version in a group produces identical compiled classes. That was measured once;
this checks it on every push, because what would break it - Mojang renaming a member this mod
touches, or Fabric's intermediary moving - arrives without warning and produces a jar that installs
happily and misbehaves quietly.

IT HAS ALREADY EARNED ITS KEEP. On its first run it found that 1.21.10 and 1.21.11 share a Fabric
jar but not a NeoForge one: a Fabric jar is remapped to intermediary, which survives a Minecraft
rename, while NeoForge runs on official names where the same rename lands in the bytecode. Hence
groups are declared per loader.

Reads the groups out of build.gradle, so membership is written down in exactly one place, and reads
one fingerprint file per version from fingerprint_jar.py. Parsing the build file rather than asking
Gradle keeps this job free of a JDK and a toolchain setup for what is, in the end, nine lines.

  python tools/check_groups.py <loader> <fingerprint dir>
"""
import os
import re
import sys

BUILD_FILE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build.gradle")


def groups_from_build(loader):
    """The GROUPS entry for one loader, from build.gradle, where membership is declared."""
    text = open(BUILD_FILE, encoding="utf-8").read()
    block = re.search(r"def GROUPS = \[(.*?)\n\]\n", text, re.S)
    if not block:
        raise SystemExit("could not find the GROUPS map in build.gradle")
    section = re.search(r"\b" + re.escape(loader) + r"\s*:\s*\[(.*?)\n\s*\]", block.group(1), re.S)
    if not section:
        raise SystemExit("no %r section in the GROUPS map" % loader)
    return [re.findall(r"'([^']+)'", line)
            for line in section.group(1).splitlines() if "'" in line]


def read_fingerprints(root):
    """version -> hash. download-artifact gives each artifact its own directory."""
    out = {}
    if not os.path.isdir(root):
        return out
    for name in sorted(os.listdir(root)):
        path = os.path.join(root, name)
        if os.path.isdir(path):
            for inner in os.listdir(path):
                out[name] = open(os.path.join(path, inner), encoding="utf-8").read().strip()
        else:
            out[os.path.splitext(name)[0]] = open(path, encoding="utf-8").read().strip()
    return out


def main(argv):
    if len(argv) != 3:
        raise SystemExit("usage: check_groups.py <loader> <fingerprint dir>")
    loader, root = argv[1], argv[2]
    prints = read_fingerprints(root)

    problems = []
    for group in groups_from_build(loader):
        seen = {v: prints[v] for v in group if v in prints}
        missing = [v for v in group if v not in prints]
        label = " ".join(group)
        if not seen:
            print("SKIP  %s (nothing fingerprinted)" % label)
            continue
        if len(set(seen.values())) == 1:
            print("OK    %-46s %d version(s) share one jar" % (label, len(seen)))
        else:
            print("DRIFT %s" % label)
            for version, value in sorted(seen.items()):
                print("        %-10s %s" % (version, value[:16]))
            problems.append(label)
        if missing:
            print("      (not fingerprinted: %s)" % ", ".join(missing))

    if problems:
        print("")
        print("%d %s group(s) no longer share a jar. Either split the group in build.gradle, or"
              % (len(problems), loader))
        print("find what moved - a version whose classes differ cannot ship its group's jar.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
