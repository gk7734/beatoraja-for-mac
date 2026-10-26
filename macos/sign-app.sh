#!/bin/bash
# Signs beatoraja.app for notarization: hardened runtime and a secure timestamp on every Mach-O, inside out.
# The notary service also inspects jars, so the native libraries and executables packed in lib/*.jar are
# unpacked, signed and written back first. Usage: sign-app.sh <beatoraja.app> <Developer ID identity>
set -euo pipefail
APP="$1"
ID="$2"
PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
ENTITLEMENTS="$PROJECT/macos/java.entitlements"
python3 - "$APP" "$ID" "$ENTITLEMENTS" <<'PY'
import hashlib, pathlib, subprocess, sys, tempfile, zipfile
app, identity, entitlements = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
MACHO = {b'\xcf\xfa\xed\xfe', b'\xce\xfa\xed\xfe', b'\xca\xfe\xba\xbe', b'\xbe\xba\xfe\xca'}

def sign(paths, *extra):
    if paths:
        subprocess.run(['codesign', '--force', '--timestamp', '--options', 'runtime', '--sign', identity, *extra,
                        *map(str, paths)], check=True)

def is_macho(path):
    with open(path, 'rb') as f:
        return f.read(4) in MACHO

# 1. Mach-O entries inside jars (class files share the fat-binary magic, so they are skipped by name).
def rewrite(jar, replace):
    out = jar.with_suffix('.signing')
    with zipfile.ZipFile(jar) as z, zipfile.ZipFile(out, 'w') as dst:
        for i in z.infolist():
            dst.writestr(i, replace(i.filename, z.read(i)), compress_type=i.compress_type)
    out.replace(jar)

jars = sorted((app / 'Contents/app/lib').glob('*.jar'))
hashes = {}
with tempfile.TemporaryDirectory() as tmp:
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            entries = [i for i in z.infolist() if not i.is_dir() and not i.filename.endswith('.class')
                       and z.open(i).read(4) in MACHO]
            work = pathlib.Path(tmp) / jar.stem
            for i in entries:
                z.extract(i, work)
        if not entries:
            continue
        sign([work / i.filename for i in entries])
        signed = {i.filename: (work / i.filename).read_bytes() for i in entries}
        for name, data in signed.items():
            hashes[f'META-INF/{name}.sha1'] = hashlib.sha1(data).hexdigest().encode()
        rewrite(jar, lambda name, data: signed.get(name, data))
        print(f'signed {len(entries)} in {jar.name}')
# LWJGL compares each library with META-INF/<path>.sha1 in both its class and natives jars and reports a version
# mismatch otherwise, so those records get the signed libraries' hashes.
for jar in jars:
    with zipfile.ZipFile(jar) as z:
        if not hashes.keys() & set(z.namelist()):
            continue
    rewrite(jar, lambda name, data: hashes[name] + data[40:] if name in hashes else data)

# 2. Loose Mach-O files, innermost first: libraries and helpers, then the JVM launchers with their exceptions.
home = app / 'Contents/runtime/Contents/Home'
main = app / 'Contents/MacOS/beatoraja'
files = [p for p in (app / 'Contents').rglob('*') if p.is_file() and not p.is_symlink() and p != main and is_macho(p)]
launchers = [p for p in files if p.parent == home / 'bin']
sign([p for p in files if p not in launchers])
sign(launchers, '--entitlements', entitlements)
print(f'signed {len(files)} loose binaries ({len(launchers)} JVM launchers)')

# 3. The runtime bundle, then the app (the Swift launcher needs no exceptions).
sign([app / 'Contents/runtime'])
sign([app])
PY
codesign --verify --deep --strict "$APP"
