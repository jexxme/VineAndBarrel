#!/usr/bin/env bash
# Ports commits from the multiloader layout (common/, neoforge/, fabric/) to a branch that is still on the
# single-loader layout (src/...: 26.2, 26.1.2, 26.1). The multiloader counterpart of `git cherry-pick -x`.
#
#   bash <multiloader checkout>/scripts/multiloader/backport.sh [--map-build-gradle] <target-branch> <commit|range>...
#
#   e.g., from a worktree that has 26.2 checked out:
#     git worktree add ../vb-26.2 26.2 && cd ../vb-26.2
#     bash ../vine-and-barrel/scripts/multiloader/backport.sh 26.2 abc123
#     bash ../vine-and-barrel/scripts/multiloader/backport.sh 26.2 abc123^..def456
#
# Where it applies: the checkout of the current directory (not the script's). It must have <target-branch> checked
# out (the argument is a guard against applying onto the wrong branch), no staged or unstaged changes, and no
# am / rebase / merge / cherry-pick in progress. It never pushes.
#
# What it does, before touching anything:
#   - Paths are rewritten: common/src/X -> src/X, neoforge/src/X -> src/X (the three NeoForge-only classes that
#     moved into the neoforge package go back to world/, kettle/ and barrel/). Root files (changelog.md, README.md,
#     gradle.properties, site/, .github/, ...) stay as they are.
#   - Refused outright, with the files named: anything under fabric/, build-logic/, scripts/multiloader/, the
#     multiloader build files (build.gradle, settings.gradle, common/build.gradle, neoforge/ outside src/) and
#     PARITY.md, and any src/ path (that commit is not on the multiloader layout). neoforge/build.gradle is refused
#     too unless --map-build-gradle is given: it then goes to build.gradle, which differs a lot on the old layout,
#     so even a clean merge there needs a look by hand (references to :common or build-logic don't belong there).
#   - Every file a commit modifies, deletes or renames must exist under its new path on the target (taking earlier
#     commits of the same run into account), and a file it adds must not exist yet. Otherwise it stops and lists
#     them: the three-way fallback of git am would otherwise look for a similar file and land the edit in the wrong
#     one (e.g. an edit to apple_juice.json applied to wheat_wort.json on a branch without apple juice).
# Then it runs git am -3 with rename detection off. Each commit message gets "(cherry picked from commit <sha>)".
# On a conflict git am stops: resolve, git add, then `git -c merge.renames=false am --continue` (the remaining
# commits follow), or git am --abort to get back to where you were.
# Afterwards build and test that branch; after a change to datagen, run ./gradlew runData there and commit the result
# (see CONTRIBUTING.md).
set -euo pipefail

J=src/main/java/jexxme/vineandbarrel
map_build_gradle=false
args=()
for arg in "$@"; do
    case "$arg" in
        --map-build-gradle) map_build_gradle=true ;;
        -h|--help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
        -*) echo "Unknown option: $arg (see --help)" >&2; exit 2 ;;
        *) args+=("$arg") ;;
    esac
done
if [ ${#args[@]} -lt 2 ]; then
    echo "Usage: backport.sh [--map-build-gradle] <target-branch> <commit|range>...   (see --help)" >&2
    exit 2
fi
target=${args[0]}
revs=("${args[@]:1}")

die() { printf '%s\n' "$@" >&2; exit 1; }

# --- the checkout to apply in
top=$(git rev-parse --show-toplevel 2>/dev/null) || die "Not inside a git checkout."
cd "$top"
git rev-parse --verify -q "refs/heads/$target" >/dev/null || die "No local branch '$target'."
current=$(git symbolic-ref -q --short HEAD || true)
[ "$current" = "$target" ] || die "This checkout ($top) has '${current:-a detached HEAD}' checked out, not '$target'." \
    "Run this from a worktree of $target, e.g.: git worktree add ../vb-$target $target"
for state in rebase-apply rebase-merge MERGE_HEAD CHERRY_PICK_HEAD REVERT_HEAD; do
    [ ! -e "$(git rev-parse --git-path "$state")" ] || die "A git operation is in progress here ($state): finish or abort it first."
done
[ -z "$(git status --porcelain --untracked-files=no)" ] || die "The checkout has uncommitted changes: commit or set them aside first."
git cat-file -e "HEAD:src/main/java" 2>/dev/null && ! git cat-file -e "HEAD:common/src" 2>/dev/null \
    || die "'$target' is not on the single-loader layout (src/main/java, no common/src): nothing to port."

# --- the commits, oldest first
commits=()
for rev in "${revs[@]}"; do
    if [[ "$rev" == *..* ]]; then
        list=$(git rev-list --reverse "$rev") || die "Not a valid range: $rev"
        [ -n "$list" ] || die "The range $rev has no commits."
        while IFS= read -r c; do commits+=("$c"); done <<< "$list"
    else
        c=$(git rev-parse --verify -q "$rev^{commit}") || die "Not a commit: $rev"
        commits+=("$c")
    fi
done
for c in "${commits[@]}"; do
    if git rev-parse --verify -q "$c^2" >/dev/null; then
        die "$(git log --oneline -1 "$c") is a merge commit: port the commits it brought in instead."
    fi
done

# --- where a path of the multiloader layout goes on the single-loader layout; "!" = refused, with the reason
map_path() {
    case "$1" in
        neoforge/$J/neoforge/AddFeaturesByFilterBiomeModifier.java) echo "$J/world/AddFeaturesByFilterBiomeModifier.java" ;;
        neoforge/$J/neoforge/transfer/BrewKettleItemHandler.java) echo "$J/kettle/BrewKettleItemHandler.java" ;;
        neoforge/$J/neoforge/transfer/BarrelItemHandler.java) echo "$J/barrel/BarrelItemHandler.java" ;;
        common/src/*) echo "${1#common/}" ;;
        neoforge/src/*) echo "${1#neoforge/}" ;;
        neoforge/build.gradle) if $map_build_gradle; then echo build.gradle; else echo "!NeoForge build file; --map-build-gradle ports it to build.gradle"; fi ;;
        fabric/*) echo "!Fabric only" ;;
        build-logic/*|common/*|neoforge/*|build.gradle|settings.gradle|PARITY.md) echo "!multiloader build only" ;;
        scripts/multiloader/*) echo "!multiloader tooling" ;;
        src/*) echo "!not a multiloader path (is this commit from an old-layout branch?)" ;;
        \"*) echo "!unusual characters in the path, port by hand" ;;
        *) echo "$1" ;;
    esac
}

# --- check everything before applying anything
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
git ls-tree -r --name-only HEAD > "$work/exists"
refused=()
missing=()
for c in "${commits[@]}"; do
    short=$(git log --format='%h %s' -1 "$c")
    # status, then one path (two for renames and copies), tab-separated
    while IFS=$'\t' read -r status p1 p2; do
        for p in "$p1" ${p2:+"$p2"}; do
            m=$(map_path "$p")
            [[ "$m" == !* ]] && refused+=("$short: $p (${m#!})")
        done
    done < <(git -c core.quotePath=false diff-tree -r --no-commit-id -M --name-status "$c^" "$c")
done
if [ ${#refused[@]} -gt 0 ]; then
    echo "Refused, nothing applied: these commits touch files that have no place on the single-loader layout." >&2
    printf '  %s\n' "${refused[@]}" >&2
    echo "Split them (the common part in its own commit) or port those parts by hand." >&2
    exit 1
fi
for c in "${commits[@]}"; do
    short=$(git log --format='%h %s' -1 "$c")
    while IFS=$'\t' read -r status p1 p2; do
        from=$(map_path "$p1")
        case "$status" in
            A*)
                if grep -qxF -- "$from" "$work/exists"; then missing+=("$short: adds $from, which is already on $target")
                else echo "$from" >> "$work/exists"; fi ;;
            R*|C*)
                to=$(map_path "$p2")
                if ! grep -qxF -- "$from" "$work/exists"; then missing+=("$short: ${status:0:1} $from (from $p1) is not on $target")
                elif grep -qxF -- "$to" "$work/exists"; then missing+=("$short: ${status:0:1} to $to, which is already on $target")
                else
                    [[ "$status" == R* ]] && grep -vxF -- "$from" "$work/exists" > "$work/exists.new" && mv "$work/exists.new" "$work/exists"
                    echo "$to" >> "$work/exists"
                fi ;;
            D*)
                if ! grep -qxF -- "$from" "$work/exists"; then missing+=("$short: deletes $from (from $p1), which is not on $target")
                else grep -vxF -- "$from" "$work/exists" > "$work/exists.new" || true; mv "$work/exists.new" "$work/exists"; fi ;;
            *)
                grep -qxF -- "$from" "$work/exists" || missing+=("$short: modifies $from (from $p1), which is not on $target") ;;
        esac
    done < <(git -c core.quotePath=false diff-tree -r --no-commit-id -M --name-status "$c^" "$c")
done
if [ ${#missing[@]} -gt 0 ]; then
    echo "Stopped, nothing applied: these files are not where the commits expect them on $target." >&2
    printf '  %s\n' "${missing[@]}" >&2
    echo "The content may not exist on $target (e.g. a newer drink), or have another name there: port those parts by" \
        "hand, or leave them out." >&2
    exit 1
fi

# --- the patches, with the paths rewritten in the diff headers only (never in the changed lines)
n=0
for c in "${commits[@]}"; do
    n=$((n + 1))
    patch=$(printf '%s/%04d.patch' "$work" "$n")
    git -c core.quotePath=false format-patch -1 --stdout --binary -M --no-signature "$c" \
    | awk -v J="$J" -v sha="$c" -v mapbg="$map_build_gradle" '
        function repl(s, from, to,    out, i) {
            out = ""
            while ((i = index(s, from)) > 0) { out = out substr(s, 1, i - 1) to; s = substr(s, i + length(from)) }
            return out s
        }
        function mapline(s, pre) {   # every path in s that follows pre ("a/", "b/" or the start of a rename line)
            s = repl(s, pre "neoforge/" J "/neoforge/AddFeaturesByFilterBiomeModifier.java", pre J "/world/AddFeaturesByFilterBiomeModifier.java")
            s = repl(s, pre "neoforge/" J "/neoforge/transfer/BrewKettleItemHandler.java", pre J "/kettle/BrewKettleItemHandler.java")
            s = repl(s, pre "neoforge/" J "/neoforge/transfer/BarrelItemHandler.java", pre J "/barrel/BarrelItemHandler.java")
            s = repl(s, pre "common/src/", pre "src/")
            s = repl(s, pre "neoforge/src/", pre "src/")
            if (mapbg == "true") s = repl(s, pre "neoforge/build.gradle", pre "build.gradle")
            return s
        }
        !indiff && $0 == "---" && !noted { print ""; print "(cherry picked from commit " sha ")"; noted = 1 }
        /^diff --git / { indiff = 1; header = 1; $0 = mapline(mapline($0, " a/"), " b/") }
        header && /^(--- a\/|\+\+\+ b\/)/ { $0 = mapline(mapline($0, " a/"), " b/") }
        header && /^(rename|copy) (from|to) / { split($0, w, " "); p = w[1] " " w[2] " "; $0 = mapline($0, p) }
        header && /^(@@ |GIT binary patch|Binary files )/ { header = 0 }
        { print }
    ' > "$patch"
done

echo "Applying ${#commits[@]} commit(s) onto $target ($(git rev-parse --short HEAD)):"
for c in "${commits[@]}"; do echo "  $(git log --format='%h %s' -1 "$c")"; done
before=$(git rev-parse HEAD)
# merge.renames=false: no rename detection in the three-way fallback either
if ! git -c merge.renames=false -c diff.renames=false am -3 "$work"/*.patch; then
    echo >&2
    echo "git am stopped (conflict, or a patch that does not apply). Resolve the files (git status), git add them," \
        "then: git -c merge.renames=false am --continue (this and the remaining commits). Or git am --abort to go back"         "to $(git rev-parse --short "$before")." >&2
    exit 1
fi
echo
git log --oneline "$before..HEAD"
if git diff --name-only "$before" HEAD | grep -qE '^(src/generated/|src/main/java/jexxme/vineandbarrel/data/)'; then
    echo "Datagen files changed: run ./gradlew runData on $target and commit the result."
fi
echo "Applied on $target, not pushed. Build and test this branch before pushing."
