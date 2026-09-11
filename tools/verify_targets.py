#!/usr/bin/env python3
"""
Check every mixin target against a Minecraft client jar - for one version, or many.

WHY THIS EXISTS. Compiling proves nothing about mixins. A target is a STRING resolved when the
game launches ("shouldRender", or a full descriptor like
"handleCustomPayload(L.../ClientboundCustomPayloadPacket;)V"), so a method Mojang renamed compiles
perfectly and then fails at startup with "injection failed". The Java compiler never looks at it.
That gap is survivable on one version and is the whole problem on several: a port's first symptom
is a mixin that silently matches nothing.

Run it over a list of versions and the output IS the compatibility matrix - which versions the mod
can target at all, and precisely what breaks on the ones it cannot. That answers "how far back can
this be backported" with evidence rather than recollection.

  python tools/verify_targets.py                      # the version in gradle.properties
  python tools/verify_targets.py 26.2 26.1 1.21.8     # a matrix

Needs only Python and network access on first run for each version; client jars are
cached under build/minecraft/<version>/ exactly where the Gradle build puts them, so a version the
build has already fetched costs nothing.
"""
import hashlib
import io
import json
import os
import re
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MIXIN_DIR = os.path.join(ROOT, "src", "main", "java", "io", "github", "simuciokas",
                         "hidemodels", "mixin")
MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"

# Things the mod needs that are not mixin targets: the component it identifies models by, and the
# entity class that carries it. These are the real backport floor - a version without the
# item_model component cannot support the feature as designed, however well the mixins resolve.
EXTRA_CLASSES = [
    ("net.minecraft.world.entity.Display$ItemDisplay", "the entity the models ride on"),
]
EXTRA_FIELDS = [
    ("net.minecraft.core.component.DataComponents", "ITEM_MODEL",
     "the component the whole feature keys on"),
]


# ---------------------------------------------------------------- reading the mod's own sources
def parse_mixins():
    """
    Every (class, member, descriptor) the mod injects into, read from the sources themselves.

    Deliberately not a hand-kept list: a list would drift from the code, and a drifted checker is
    worse than none because it reports success about the wrong thing.
    """
    out = []
    for name in sorted(os.listdir(MIXIN_DIR)):
        if not name.endswith(".java"):
            continue
        src = open(os.path.join(MIXIN_DIR, name), encoding="utf-8").read()
        imports = dict(re.findall(r"^import\s+([\w.]+\.(\w+));", src, re.M))
        imports = {short: full for full, short in imports.items()}

        m = re.search(r"@Mixin\(([\w.$]+)\.class\)", src)
        if not m:
            continue
        target = m.group(1)
        # "Display.ItemDisplay" is a nested class: the outer name is what was imported, and the
        # jar spells the nesting with a dollar.
        head = target.split(".")[0]
        rest = target.split(".")[1:]
        full = imports.get(head, head)
        if rest:
            full = full + "$" + "$".join(rest)

        members = []
        for meth in re.findall(r'@Inject\s*\(\s*method\s*=\s*"([^"]+)"', src):
            members.append(("method", meth))
        for inv in re.findall(r'@Invoker\("([^"]+)"\)', src):
            members.append(("invoker", inv))
        for acc in re.findall(r'@Accessor\("([^"]+)"\)', src):
            members.append(("accessor", acc))
        out.append({"file": name, "class": full, "members": members})
    return out


# ---------------------------------------------------------------- getting a client jar
def version_meta(version):
    manifest = json.load(urllib.request.urlopen(MANIFEST, timeout=60))
    entry = next((v for v in manifest["versions"] if v["id"] == version), None)
    if entry is None:
        raise SystemExit("Mojang's manifest lists no version %r" % version)
    return json.load(urllib.request.urlopen(entry["url"], timeout=60))


def _fetch(url, dest, want_sha1=None):
    if os.path.isfile(dest):
        return dest
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    part = dest + ".part"
    with urllib.request.urlopen(url, timeout=600) as r, open(part, "wb") as fh:
        fh.write(r.read())
    if want_sha1 and hashlib.sha1(open(part, "rb").read()).hexdigest() != want_sha1:
        os.remove(part)
        raise SystemExit("sha1 mismatch downloading %s" % url)
    os.replace(part, dest)
    return dest


def client_jar(version):
    """
    (jar, mappings-or-None) for a version, cached where the Gradle build caches its jar.

    THE MAPPINGS ARE THE POINT. 26.x ships a client jar already in readable official names and
    publishes no mapping file - which is why this mod can be built with no Loom at all. EVERY
    EARLIER VERSION ships an OBFUSCATED jar plus a separate client_mappings, so a class the mod
    calls net.minecraft.client.multiplayer.ClientPacketListener is spelled something like "fud" in
    the file. Looking for the readable name there finds nothing and reports the class as absent -
    which is a false negative, and exactly the wrong answer to give about a backport target.
    """
    meta = version_meta(version)
    root = os.path.join(ROOT, "build", "minecraft", version)
    dl = meta["downloads"]
    jar = _fetch(dl["client"]["url"], os.path.join(root, "client.jar"), dl["client"]["sha1"])
    maps = None
    if "client_mappings" in dl:
        maps = _fetch(dl["client_mappings"]["url"], os.path.join(root, "client.txt"),
                      dl["client_mappings"]["sha1"])
    return jar, maps


def load_mappings(path):
    """
    ProGuard client_mappings -> {official class: obf class} and {(official class, method): obf}.

    The file reads "official -> obfuscated:", with members indented under their class:
        net.minecraft.client.Minecraft -> fud:
            void run() -> a
    Only names are needed here, not line numbers, so the leading "12:34:" on member lines is
    dropped.
    """
    classes, methods, fields = {}, {}, {}
    cur = None
    for raw in io.open(path, encoding="utf-8"):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        if not raw.startswith(" ") and not raw.startswith("	"):
            left, right = raw.rstrip().rsplit(" -> ", 1)
            cur = left.strip()
            classes[cur] = right.rstrip(":").strip()
            continue
        if cur is None:
            continue
        body, obf = raw.rstrip().rsplit(" -> ", 1)
        body = body.strip()
        if ":" in body.split(" ")[0]:
            body = body.split(":")[-1].strip() if body.count(":") < 3 else body.split(":", 2)[2].strip()
        if "(" in body:
            name = body.split("(")[0].split()[-1]
            methods[(cur, name)] = obf.strip()
        else:
            fields[(cur, body.split()[-1])] = obf.strip()
    return classes, methods, fields


# ---------------------------------------------------------------- asking the jar what it has
_CACHE = {}


def _read_class(blob):
    """
    (name, descriptor) for every field and method in one class file.

    A CLASS-FILE PARSER RATHER THAN javap, because javap refuses any class newer than its own JDK
    ("Unsupported class file version: 69") - and this tool exists to compare versions that need
    DIFFERENT JDKs: 26.2 is class-file 69, 1.21.x is 65. Requiring a matching JDK per version would
    make the matrix impossible to produce from one machine, and impossible in CI without a matrix
    of JDKs to go with it. The format's constant pool and member tables are stable across every
    version that has ever existed, so parsing them costs less than that would.
    """
    import struct
    if len(blob) < 10 or struct.unpack_from(">I", blob, 0)[0] != 0xCAFEBABE:
        return []
    pos = 10                                   # magic(4) minor(2) major(2) -> constant_pool_count
    count = struct.unpack_from(">H", blob, 8)[0]
    pool, i = {}, 1
    while i < count:
        tag = blob[pos]; pos += 1
        if tag == 1:                           # Utf8
            ln = struct.unpack_from(">H", blob, pos)[0]; pos += 2
            pool[i] = blob[pos:pos + ln].decode("utf-8", "replace"); pos += ln
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag in (5, 6):                    # Long/Double eat TWO pool slots - the classic trap
            pos += 8; i += 1
        elif tag in (7, 8, 16, 19, 20):
            pos += 2
        elif tag == 15:
            pos += 3
        else:
            return []                          # unknown tag: refuse rather than guess
        i += 1
    pos += 6                                   # access_flags, this_class, super_class
    ifn = struct.unpack_from(">H", blob, pos)[0]; pos += 2 + ifn * 2

    out = []
    for _ in range(2):                         # fields, then methods - identical shape
        n = struct.unpack_from(">H", blob, pos)[0]; pos += 2
        for _ in range(n):
            _acc, nidx, didx, acount = struct.unpack_from(">HHHH", blob, pos); pos += 8
            out.append((pool.get(nidx, "?"), pool.get(didx, "?")))
            for _ in range(acount):
                alen = struct.unpack_from(">I", blob, pos + 2)[0]
                pos += 6 + alen
    return out


def members_of(jar, cls):
    """Method and field signatures of one class, or None when the class is not in the jar."""
    import zipfile
    key = (jar, cls)
    if key in _CACHE:
        return _CACHE[key]
    # Dots are package separators, "$" already spells the nesting, and an obfuscated name may have
    # no package at all - so one substitution covers every shape.
    path = cls.replace(".", "/") + ".class"
    try:
        zf = zipfile.ZipFile(jar)
        blob = zf.read(path)
    except KeyError:
        _CACHE[key] = None
        return None
    except Exception:
        _CACHE[key] = None
        return None
    _CACHE[key] = _read_class(blob)
    return _CACHE[key]


def map_descriptor(desc, classes):
    """Rewrite the object types inside a descriptor into their obfuscated names."""
    def one(m):
        official = m.group(1).replace("/", ".")
        return "L" + classes.get(official, official).replace(".", "/") + ";"
    return re.sub(r"L([\w/$]+);", one, desc)


def check_version(version, mixins):
    """Returns (ok, [problem, ...]) for one Minecraft version."""
    try:
        jar, maps = client_jar(version)
    except SystemExit as e:
        return False, [str(e)]
    classes, methods, fields = ({}, {}, {}) if maps is None else load_mappings(maps)
    obf_class = lambda c: classes.get(c, c)
    problems = []

    for mx in mixins:
        target = obf_class(mx["class"])
        found = members_of(jar, target)
        if found is None:
            problems.append("%s: class %s is absent" % (mx["file"], mx["class"]))
            continue
        names = {n for n, _ in found}
        sigs = {n + d for n, d in found}
        for kind, member in mx["members"]:
            if "(" in member:
                want_name = member.split("(")[0]
                want_desc = map_descriptor(member[len(want_name):], classes)
                obf_name = methods.get((mx["class"], want_name), want_name)
                if obf_name + want_desc in sigs:
                    continue
                if obf_name not in names:
                    problems.append("%s: %s.%s is absent" % (mx["file"], mx["class"], want_name))
                else:
                    problems.append("%s: %s.%s exists but NOT with the declared descriptor %s"
                                    % (mx["file"], mx["class"], want_name, member[len(want_name):]))
            else:
                obf_name = (methods.get((mx["class"], member))
                            or fields.get((mx["class"], member)) or member)
                if obf_name not in names:
                    problems.append("%s: %s %s.%s is absent"
                                    % (mx["file"], kind, mx["class"], member))

    for cls, why in EXTRA_CLASSES:
        if members_of(jar, obf_class(cls)) is None:
            problems.append("%s is absent (%s)" % (cls, why))
    for cls, field, why in EXTRA_FIELDS:
        found = members_of(jar, obf_class(cls))
        if found is None:
            problems.append("%s is absent (%s)" % (cls, why))
        else:
            obf_field = fields.get((cls, field), field)
            if obf_field not in {n for n, _ in found}:
                problems.append("%s.%s is absent (%s)" % (cls, field, why))

    return (not problems), problems


def main(argv):
    versions = argv[1:]
    if not versions:
        props = open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8").read()
        versions = [re.search(r"^minecraft_version=(.+)$", props, re.M).group(1).strip()]

    mixins = parse_mixins()
    print("checking %d mixin targets from %d files\n"
          % (sum(len(m["members"]) for m in mixins), len(mixins)))

    worst = 0
    for v in versions:
        ok, problems = check_version(v, mixins)
        print("%-8s %s" % (v, "OK - every target resolves" if ok
                           else "%d problem%s" % (len(problems), "" if len(problems) == 1 else "s")))
        for p in problems:
            print("           %s" % p)
        if not ok:
            worst = 1
    return worst


if __name__ == "__main__":
    sys.exit(main(sys.argv))
