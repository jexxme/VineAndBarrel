#!/usr/bin/env bash
# The automated checks on both loaders, locally (the CI workflow for now; nothing here talks to the internet except
# Gradle's dependency downloads). Two tiers:
#
#   bash scripts/multiloader/verify-all.sh          quick (also --quick), the default, after each round of changes
#                                                   (about 4 minutes):
#       - build: both jars, Error Prone, forbidden patterns
#       - datagen is up to date (common/src/generated), only when the data generators or common/src/generated
#         changed since the last green datagen check
#       - the GameTests on NeoForge and on Fabric, without Farmer's Delight; the run logs have no ERROR lines
#       - the singleplayer Fabric smoke test, without FD (a client in a hidden window)
#     A GameTest or smoke run whose inputs (the sources, the build files, the run folder's settings and mods) are
#     exactly those of its last green run is not run again. Delete build/verify-all to run everything anyway.
#
#   bash scripts/multiloader/verify-all.sh --full   everything, before merging and before tagging (about 15 minutes):
#       - build and datagen, always
#       - the six GameTest runs: NeoForge and Fabric, each without and with Farmer's Delight and with the compat mods
#       - the six Fabric smoke tests: singleplayer, a LAN host, a dedicated server, each without and with FD
#         Refabricated (screenshots in fabric/run/smoke-*/smoke). The dedicated server needs the Minecraft EULA
#         accepted by the player in fabric/run/server-smoke/eula.txt; this script never accepts it, and without it
#         the run is reported as not run (and the result is not green).
#       - the cross-version smoke tests (also on that server): our client on a Fabric server without V&B, and a
#         client without V&B refused by ours
#       - the parity checklist, PARITY.md, from all of the above
#     Nothing is skipped, whatever ran before.
#
#   --fd        quick only: also the Farmer's Delight GameTests on both loaders and the singleplayer smoke test with FD
#               Refabricated (full has them anyway)
#   --no-smoke  no smoke tests (no game client); with --full the parity checklist is left out too, and the run is not
#               enough for a release
#
#   --part=<name>  only one part of the tier, for GitHub's runners, where the parts of one verify run as jobs side by
#                   side (.github/workflows/verify.yml), or to run just that part here. The parts: static (build and
#                   datagen), gametests, gametests-fd, smoke-client, smoke-client-fd, smoke-lan, smoke-lan-fd,
#                   smoke-server, smoke-server-fd, smoke-cross (the cross-version tests); quick has static, gametests
#                   and smoke-client. A part writes its result to build/verify-all/part-<name>.result.
#   --parts         prints the parts of the tier as a JSON list and stops
#   --collect       runs no game: reads the results of all the parts of the tier (each must be there and green), prints
#                   them as one, and with --full builds the parity checklist from their reports
#
# At most two games run at once (GameTest servers and smoke clients; see vb-loader.gradle): the machine's memory is
# shared. Exit code 0 only if everything that was asked for passed.
set -uo pipefail
cd "$(dirname "$0")/../.."

full=false fd=false smoke=true part='' collect=false list_parts=false
for arg in "$@"; do
    case "$arg" in
        --quick) full=false ;;
        --full) full=true ;;
        --fd) fd=true ;;
        --no-smoke) smoke=false ;;
        --part=*) part=${arg#--part=} ;;
        --parts) list_parts=true ;;
        --collect) collect=true ;;
        -h|--help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
        *) echo "Unknown option: $arg (see --help)" >&2; exit 2 ;;
    esac
done

# The parts of this tier, in the order they run here
tier_parts=(static gametests)
{ $full || $fd; } && tier_parts+=(gametests-fd)
if $smoke; then
    tier_parts+=(smoke-client)
    { $full || $fd; } && tier_parts+=(smoke-client-fd)
    $full && tier_parts+=(smoke-lan smoke-lan-fd smoke-server smoke-server-fd smoke-cross)
fi
if $list_parts; then
    printf '["%s"' "${tier_parts[0]}"; printf ',"%s"' "${tier_parts[@]:1}"; printf ']\n'
    exit 0
fi
if [ -n "$part" ] && [[ " ${tier_parts[*]} " != *" $part "* ]]; then
    echo "No part '$part' in this tier (its parts: ${tier_parts[*]})" >&2
    exit 2
fi
# Whether this run does that part: all of the tier's parts, or only the one asked for; none when collecting
does() {
    $collect && return 1
    [[ " ${tier_parts[*]} " == *" $1 "* ]] && { [ -z "$part" ] || [ "$part" = "$1" ]; }
}

state=build/verify-all # the fingerprints of the last green runs, and each Gradle invocation's output
mkdir -p "$state"
failed=()
timings=()
started=$SECONDS
step() { printf '\n==== %s\n' "$*"; }
took() { timings+=("$(printf '%-44s %4ds' "$1" "$2")"); }
py=python; command -v python >/dev/null 2>&1 || py=python3
fingerprint() { "$py" scripts/multiloader/fingerprint.py "$@" | tr -d '\r'; }

# One Gradle invocation, its output also in build/verify-all/<name>.log; returns Gradle's exit code
gradle() {
    local name=$1; shift
    local begin=$SECONDS
    ./gradlew "$@" --continue --console=plain 2>&1 | tee "$state/$name.log"
    local code=${PIPESTATUS[0]}
    took "gradle $name" $((SECONDS - begin))
    return "$code"
}
# Whether a task ran in that invocation and did not fail
task_ok() { grep -qx "> Task $1" "$state/$2.log" && ! grep -qx "> Task $1 FAILED" "$state/$2.log"; }
# How long a game ran, from the first and the last time stamp in its log (NeoForge's and Fabric's formats both)
log_seconds() {
    [ -f "$1" ] || { echo '?'; return; }
    local first last
    first=$(grep -m1 -oE '[0-9]{2}:[0-9]{2}:[0-9]{2}' "$1")
    last=$(tail -n 50 "$1" | grep -oE '^\[[^]]*[0-9]{2}:[0-9]{2}:[0-9]{2}' | tail -n 1 | grep -oE '[0-9]{2}:[0-9]{2}:[0-9]{2}$')
    [ -n "$first" ] && [ -n "$last" ] || { echo '?'; return; }
    local a b
    a=$((10#${first:0:2} * 3600 + 10#${first:3:2} * 60 + 10#${first:6:2}))
    b=$((10#${last:0:2} * 3600 + 10#${last:3:2} * 60 + 10#${last:6:2}))
    echo $(( (b - a + 86400) % 86400 ))
}
# This older-Minecraft branch only: ERROR lines other mods log about their own data on this Minecraft version, which
# change nothing for our tests (one extended regex per line in known-errors.txt, matched against the whole line)
known_errors=scripts/multiloader/known-errors.txt
errors_in_log() { # prints the first ERROR lines and fails if there are any
    if [ ! -f "$1" ]; then
        echo "no log: $1"
        return 1
    fi
    local found
    found=$(grep '/ERROR\]' "$1" | { if [ -s "$known_errors" ]; then grep -vEf <(grep -v '^#' "$known_errors" | tr -d '\r'); else cat; fi; })
    if [ -n "$found" ]; then
        echo "$found" | head -5
        return 1
    fi
}

# What every game run depends on: all common and loader sources, the build files, Gradle's own files
base_inputs=(common/src build-logic/src build-logic/build.gradle build-logic/settings.gradle build.gradle settings.gradle
    gradle.properties gradle common/build.gradle)
neoforge_inputs=("${base_inputs[@]}" neoforge/src/main neoforge/build.gradle)
fabric_inputs=("${base_inputs[@]}" fabric/src/main fabric/build.gradle)

# The GameTest and smoke runs: name, Gradle task, the game's log, then its inputs besides the sources (the run folder's
# settings and mods; worlds, logs and screenshots are no input, see fingerprint.py)
declare -A run_task run_log run_inputs
add_run() {
    local name=$1 task=$2 log=$3; shift 3
    run_task[$name]=$task
    run_log[$name]=$log
    run_inputs[$name]="$*"
}
add_run neoforge :neoforge:runGameTestServer neoforge/run/logs/latest.log "${neoforge_inputs[*]}" neoforge/run/config neoforge/run/defaultconfigs neoforge/run/mods
add_run neoforge-fd :neoforge:runGameTestServerFd neoforge/run-fd/logs/latest.log "${neoforge_inputs[*]}" neoforge/run-fd/config neoforge/run-fd/mods
add_run neoforge-compat :neoforge:runGameTestServerCompat neoforge/run-compat/logs/latest.log "${neoforge_inputs[*]}" neoforge/run-compat/config neoforge/run-compat/mods
add_run fabric :fabric:runGameTest fabric/run/gametest/logs/latest.log "${fabric_inputs[*]}" fabric/run/gametest/config fabric/run/gametest/mods fabric/run/gametest/server.properties
add_run fabric-fd :fabric:runGameTestFd fabric/run-fd/logs/latest.log "${fabric_inputs[*]}" fabric/run-fd/config fabric/run-fd/mods fabric/run-fd/server.properties
add_run fabric-compat :fabric:runGameTestCompat fabric/run-compat/logs/latest.log "${fabric_inputs[*]}" fabric/run-compat/config fabric/run-compat/mods fabric/run-compat/server.properties
add_run smoke-client :fabric:runSmokeClient fabric/run/smoke-client/logs/latest.log "${fabric_inputs[*]}" fabric/src/smoketest fabric/run/smoke-client/config
add_run smoke-client-fd :fabric:runSmokeClientFd fabric/run/smoke-client-fd/logs/latest.log "${fabric_inputs[*]}" fabric/src/smoketest fabric/run/smoke-client-fd/config

# Runs the given runs in one Gradle invocation (with build first, when asked), skipping those whose inputs match their
# last green run when quick; checks each one's log and records its time and its fingerprint once it is green
run_games() {
    local invocation=$1 with_build=$2; shift 2
    local tasks=() todo=() name
    for name in "$@"; do
        # shellcheck disable=SC2086
        if ! $full && [ -f "$state/$name.green" ] && [ "$(cat "$state/$name.green")" = "$(fingerprint ${run_inputs[$name]})" ]; then
            echo "$name: same inputs as its last green run, not run again"
            took "$name (skipped, unchanged)" 0
            continue
        fi
        tasks+=("${run_task[$name]}")
        todo+=("$name")
    done
    $with_build && tasks=(build "${tasks[@]}")
    [ ${#tasks[@]} -eq 0 ] && return
    gradle "$invocation" "${tasks[@]}" || true
    if $with_build; then
        local project
        for project in common neoforge fabric; do
            task_ok ":$project:build" "$invocation" || { failed+=("build of $project (see $state/$invocation.log)"); break; }
        done
    fi
    for name in "${todo[@]}"; do
        local task=${run_task[$name]} log=${run_log[$name]} ok=true
        if ! task_ok "$task" "$invocation"; then
            failed+=("$name: $task failed or did not run (see $state/$invocation.log)")
            ok=false
        fi
        if [[ $task == *Smoke* ]]; then
            local run=${task#:fabric:}
            local check=":fabric:check${run^}" # its finalizer, e.g. checkRunSmokeClient
            task_ok "$check" "$invocation" || { failed+=("$name: $check failed"); ok=false; }
        elif ! errors_in_log "$log"; then
            failed+=("$name: ERROR lines in $log")
            ok=false
        fi
        took "$name" "$(log_seconds "$log")"
        # shellcheck disable=SC2086
        $ok && fingerprint ${run_inputs[$name]} > "$state/$name.green"
    done
}

tier=$($full && echo full || echo quick)
tier_name=$tier # the name of the tier's own Gradle invocation and of its log
$fd && ! $full && tier+=", with FD"
$smoke || tier+=", no smoke"
if $collect; then echo "verify-all: $tier, the results of its parts"
elif [ -n "$part" ]; then echo "verify-all: $tier, only the part $part"
elif $full; then echo "verify-all: $tier"; else echo "verify-all: $tier (--full for everything)"; fi

# Datagen first: its run writes neoforge/run/logs/latest.log, which the NeoForge GameTest run then replaces
if does static; then
    step "datagen is up to date"
    data_imports=$(grep -rhoE '^import jexxme\.vineandbarrel\.[A-Za-z0-9_.]+' neoforge/src/main/java/jexxme/vineandbarrel/data \
        | sed 's/^import //; s/\./\//g' | sort -u)
    data_inputs=(neoforge/src/main/java/jexxme/vineandbarrel/data common/src/generated neoforge/build.gradle gradle.properties)
    for class in $data_imports; do
        # A nested class (Outer.Inner) lives in Outer.java
        for file in "$class.java" "$(dirname "$class").java"; do
            for root in common/src/main/java neoforge/src/main/java; do
                [ -f "$root/$file" ] && data_inputs+=("$root/$file")
            done
        done
    done
    if ! $full && [ -f "$state/datagen.green" ] && [ "$(cat "$state/datagen.green")" = "$(fingerprint "${data_inputs[@]}")" ]; then
        echo "The data generators and common/src/generated are unchanged since the last green datagen check: skipped"
        took "datagen (skipped, unchanged)" 0
    elif gradle datagen runData; then
        if [ -n "$(git status --porcelain -- common/src/generated)" ]; then
            git status --porcelain -- common/src/generated | head -20
            failed+=("datagen: common/src/generated is out of date (commit the runData output)")
        else
            fingerprint "${data_inputs[@]}" > "$state/datagen.green"
        fi
    else
        failed+=("runData")
    fi
fi

# The build (the static part) and the games that run alone: the GameTest runs and the singleplayer smoke tests
with_build=false
does static && with_build=true
runs=()
does gametests && runs+=(neoforge)
does gametests-fd && runs+=(neoforge-fd)
$full && does gametests && runs+=(neoforge-compat)
does gametests && runs+=(fabric)
does gametests-fd && runs+=(fabric-fd)
$full && does gametests && runs+=(fabric-compat)
does smoke-client && runs+=(smoke-client)
does smoke-client-fd && runs+=(smoke-client-fd)
if $with_build || [ ${#runs[@]} -gt 0 ]; then
    step "$($with_build && echo 'build, ')${runs[*]} (at most two games at once)"
    run_games "${part:-$tier_name}" $with_build "${runs[@]}"
fi

# The networked smoke tests: LAN and dedicated server, without and with FD Refabricated, then the cross-version tests
networked=() networked_logs=()
does smoke-lan && { networked+=(:fabric:runSmokeLan); networked_logs+=(smoke-host smoke-join); }
does smoke-lan-fd && { networked+=(:fabric:runSmokeLanFd); networked_logs+=(smoke-host-fd smoke-join-fd); }
if does smoke-server || does smoke-server-fd || does smoke-cross; then
    # eula=true as Minecraft reads it (java.util.Properties): blanks before the key and around the separator, any case
    # of true and a CRLF line end are fine; trailing blanks are not (Properties keeps them, and "true " is not true)
    if grep -qE $'^[ \t\f]*eula[ \t\f]*[=:][ \t\f]*[Tt][Rr][Uu][Ee]\r?$' fabric/run/server-smoke/eula.txt 2>/dev/null; then
        does smoke-server && { networked+=(:fabric:runSmokeServer); networked_logs+=(smoke-join-server); }
        does smoke-server-fd && { networked+=(:fabric:runSmokeServerFd); networked_logs+=(smoke-join-server-fd); }
        does smoke-cross && { networked+=(:fabric:runSmokeForeign :fabric:runSmokeRefusal); networked_logs+=(smoke-visit bare-refused); }
    else
        failed+=("dedicated-server smoke test not run: the Minecraft EULA is not accepted for the local test server (the player's call: put eula=true in fabric/run/server-smoke/eula.txt, never commit it)")
    fi
fi
if [ ${#networked[@]} -gt 0 ]; then
    step "the networked smoke tests: ${networked[*]} (one pair at a time)"
    invocation=${part:-networked}
    if gradle "$invocation" "${networked[@]}"; then :; else failed+=("networked smoke tests (see $state/$invocation.log)"); fi
    for log in "${networked_logs[@]}"; do
        took "$log" "$(log_seconds "fabric/run/$log/logs/latest.log")"
    done
fi

# Collecting: every part of the tier must have reported, and be green
if $collect; then
    for name in "${tier_parts[@]}"; do
        result=$state/part-$name.result
        if [ ! -f "$result" ]; then
            failed+=("$name: this part did not report (its job failed before the checks, or never ran)")
            continue
        fi
        problems=${#failed[@]}
        while IFS=$'\t' read -r kind text; do
            case $kind in
                time) timings+=("$text") ;;
                fail) failed+=("$text") ;;
            esac
        done < <(tail -n +2 "$result" | tr -d '\r')
        [ "$(head -n 1 "$result" | tr -d '\r')" = green ] || [ ${#failed[@]} -gt "$problems" ] || failed+=("$name: failed")
    done
fi

if $full && $smoke && [ -z "$part" ]; then
    step "parity checklist"
    "$py" scripts/multiloader/parity.py --check || failed+=("parity checklist has failures or missing runs")
elif $full && [ -z "$part" ]; then
    echo "--no-smoke: no smoke tests and no parity checklist; this run is not enough for a release"
fi

if [ -n "$part" ]; then
    took "total of $part" $((SECONDS - started))
    # For --collect, in another job on GitHub: green or failed, then this part's times and what failed
    {
        [ ${#failed[@]} -eq 0 ] && echo green || echo failed
        printf 'time\t%s\n' "${timings[@]}"
        [ ${#failed[@]} -eq 0 ] || printf 'fail\t%s\n' "${failed[@]}"
    } > "$state/part-$part.result"
elif ! $collect; then
    took "total" $((SECONDS - started))
fi
printf '\nTimes (games: from their own logs):\n'
printf '  %s\n' "${timings[@]}"
if [ ${#failed[@]} -eq 0 ]; then
    if [ -n "$part" ]; then printf '\nGREEN, the part %s only (%s)\n' "$part" "$tier"; else printf '\nALL GREEN (%s)\n' "$tier"; fi
    exit 0
fi
printf '\nFAILED:\n'
printf '  - %s\n' "${failed[@]}"
exit 1
