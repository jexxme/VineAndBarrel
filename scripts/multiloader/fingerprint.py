"""
A fingerprint of files and folders, for verify-all.sh: one SHA-1 per group of paths, over every file's path and
contents (folders recursively). A path that does not exist counts too, so a folder that appears or disappears changes
the fingerprint.

    python scripts/multiloader/fingerprint.py common/src neoforge/src/main -- fabric/src/main
    (prints one line per group, the groups separated by --)

Paths are relative to the checkout. Folders named .cache, logs, saves, screenshots, smoke and world* are skipped: the
games write them on every run, and they are no test input.
"""
import hashlib
import os
import sys

SKIP_DIRS = {'.cache', 'logs', 'saves', 'screenshots', 'smoke', 'crash-reports', 'debug'}


def skipped(name):
    return name in SKIP_DIRS or name.startswith('world') or name == 'gametestserver'


def files(top):
    if os.path.isfile(top):
        yield top
        return
    for folder, dirs, names in os.walk(top):
        dirs[:] = sorted(d for d in dirs if not skipped(d))
        for name in sorted(names):
            yield os.path.join(folder, name)


def fingerprint(paths):
    digest = hashlib.sha1()
    for top in paths:
        if not os.path.exists(top):
            digest.update(b'missing\0' + top.encode() + b'\0')
            continue
        for path in files(top):
            digest.update(path.replace(os.sep, '/').encode() + b'\0')
            with open(path, 'rb') as f:
                digest.update(hashlib.sha1(f.read()).digest())
    return digest.hexdigest()


def main(args):
    os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
    groups = [[]]
    for arg in args:
        if arg == '--':
            groups.append([])
        else:
            groups[-1].append(arg)
    for group in groups:
        print(fingerprint(group))


if __name__ == '__main__':
    main(sys.argv[1:])
