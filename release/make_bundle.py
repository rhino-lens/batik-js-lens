#!/usr/bin/env python3
"""Make the Maven Central Portal upload bundle for batik-js-lens from a finished `mvn verify`.

Reads groupId, artifactId and version from pom.xml, then writes, under
target/bundle/<group path>/<artifactId>/<version>/:
  the jar, sources jar and javadoc jar (from target/) and the POM,
  each with .md5 and .sha1 files and, if GPG_KEY is set, a detached .asc signature.
Then zips that tree into target/central-bundle.zip, ready for "Publish Component" upload.

Usage: make_bundle.py <project-root>
"""
import hashlib, os, shutil, subprocess, sys, zipfile
import xml.etree.ElementTree as ET

root = sys.argv[1]
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
pom = ET.parse(os.path.join(root, "pom.xml")).getroot()
group = pom.find("m:groupId", ns).text.strip()
artifact = pom.find("m:artifactId", ns).text.strip()
version = pom.find("m:version", ns).text.strip()
if version.endswith("-SNAPSHOT"):
    sys.exit(f"refusing to bundle a SNAPSHOT version ({version})")

target = os.path.join(root, "target")
base = f"{artifact}-{version}"
bundle_root = os.path.join(target, "bundle")
dest = os.path.join(bundle_root, *group.split("."), artifact, version)
shutil.rmtree(bundle_root, ignore_errors=True)
os.makedirs(dest)

files = {f"{base}.jar": os.path.join(target, f"{base}.jar"),
         f"{base}-sources.jar": os.path.join(target, f"{base}-sources.jar"),
         f"{base}-javadoc.jar": os.path.join(target, f"{base}-javadoc.jar"),
         f"{base}.pom": os.path.join(root, "pom.xml")}
for name, src in files.items():
    if not os.path.isfile(src):
        sys.exit(f"missing {src}: run `mvn verify` first")
    shutil.copyfile(src, os.path.join(dest, name))

key = os.environ.get("GPG_KEY")
for name in files:
    p = os.path.join(dest, name)
    data = open(p, "rb").read()
    for algo in ("md5", "sha1"):
        open(f"{p}.{algo}", "w").write(hashlib.new(algo, data).hexdigest())
    if key:
        subprocess.run(["gpg", "--batch", "--yes", "--local-user", key, "--armor",
                        "--detach-sign", "--output", p + ".asc", p], check=True)

zpath = os.path.join(target, "central-bundle.zip")
with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
    for b, dirs, names in os.walk(bundle_root):
        dirs.sort()
        for n in sorted(names):
            p = os.path.join(b, n)
            z.write(p, os.path.relpath(p, bundle_root))
print(f"wrote {zpath}")
for n in sorted(os.listdir(dest)):
    print("  ", n)
if not key:
    print("NOTE: GPG_KEY not set, so no .asc signatures; Central will reject this bundle.")
