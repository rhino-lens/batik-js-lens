#!/usr/bin/env python3
"""Make a Maven Central Portal bundle for a POM-only artifact (e.g. a relocation POM).

Reads groupId, artifactId and version from <pom>, writes the POM with .md5, .sha1 and, if
GPG_KEY is set, a detached .asc signature under <out-dir>/bundle/..., and zips the tree into
<out-dir>/<artifactId>-<version>-bundle.zip.

Usage: make_pom_bundle.py <pom> <out-dir>
"""
import hashlib, os, shutil, subprocess, sys, zipfile
import xml.etree.ElementTree as ET

pom_path, out = sys.argv[1:3]
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
pom = ET.parse(pom_path).getroot()
group, artifact, version = (pom.find(f"m:{t}", ns).text.strip() for t in ("groupId", "artifactId", "version"))
if version.endswith("-SNAPSHOT"):
    sys.exit(f"refusing to bundle a SNAPSHOT version ({version})")

bundle_root = os.path.join(out, "bundle")
shutil.rmtree(bundle_root, ignore_errors=True)
dest = os.path.join(bundle_root, *group.split("."), artifact, version)
os.makedirs(dest)
p = os.path.join(dest, f"{artifact}-{version}.pom")
shutil.copyfile(pom_path, p)
data = open(p, "rb").read()
for algo in ("md5", "sha1"):
    open(f"{p}.{algo}", "w").write(hashlib.new(algo, data).hexdigest())
key = os.environ.get("GPG_KEY")
if key:
    subprocess.run(["gpg", "--batch", "--yes", "--local-user", key, "--armor",
                    "--detach-sign", "--output", p + ".asc", p], check=True)

zpath = os.path.join(out, f"{artifact}-{version}-bundle.zip")
with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
    for b, dirs, names in os.walk(bundle_root):
        for n in sorted(names):
            fp = os.path.join(b, n)
            z.write(fp, os.path.relpath(fp, bundle_root))
print(f"wrote {zpath}")
if not key:
    print("NOTE: GPG_KEY not set, so no .asc signature; Central will reject this bundle.")
