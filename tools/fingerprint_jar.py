#!/usr/bin/env python3
"""
A hash of what a jar actually CONTAINS, ignoring what it says about itself.

Release groups exist because the jars for several Minecraft versions are byte-identical apart from
their metadata - fabric.mod.json names a different version range, and the manifest carries a
different implementation version. Those two files are exactly what SHOULD differ, so they are
excluded here and everything else is hashed.

Used by the groupCheck CI job: every version is built, fingerprinted, and compared against the rest
of its group. A version whose compiled classes drift away from its group is one that can no longer
share that jar - which is a thing to be told about loudly, because the alternative is shipping a jar
that installs happily and misbehaves quietly.
"""
import hashlib
import sys
import zipfile

IGNORED = ("fabric.mod.json", "META-INF/MANIFEST.MF", "META-INF/neoforge.mods.toml")


def fingerprint(path):
    digest = hashlib.sha256()
    with zipfile.ZipFile(path) as jar:
        for name in sorted(jar.namelist()):
            if name.endswith("/") or name in IGNORED:
                continue
            digest.update(name.encode("utf-8"))
            digest.update(jar.read(name))
    return digest.hexdigest()


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: fingerprint_jar.py <jar>")
    print(fingerprint(sys.argv[1]))
