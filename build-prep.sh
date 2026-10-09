#!/bin/sh
#
# build-prep.sh - re-apply the zhusheng JDK-selection blocks to the Gradle wrapper.
#
# Why this exists: the build needs a JDK that the Groovy compiler bundled with
# Gradle 8.7 can read (17 to 21), and that cannot be arranged from gradle.properties
# on a machine whose default `java` is newer. So gradlew / gradlew.bat pick a JDK
# themselves, in a block that Gradle's `wrapper` task does not know about and throws
# away whenever it regenerates the wrappers. Run this afterwards to put the blocks
# back; --check only reports whether they are still there.
#
# The blocks live in tools/ so that this script and build-prep.bat share one copy:
#
#   tools/gradlew-jdk17.sh    spliced into gradlew, just before it resolves JAVACMD
#   tools/gradlew-jdk17.bat   prepended to gradlew.bat, which then falls through
#                             into the script Gradle generated
#
# Usage:  ./build-prep.sh [--check]

set -u

here=$( CDPATH= cd -- "$(dirname -- "$0")" && pwd -P ) || exit 1
cd "$here" || exit 1

frag_sh=$here/tools/gradlew-jdk17.sh
frag_bat=$here/tools/gradlew-jdk17.bat
target_sh=$here/gradlew
target_bat=$here/gradlew.bat

# The block goes above this line in gradlew: the wrapper works out JAVACMD straight
# after it, so setting JAVA_HOME any later would have no effect.
anchor='# Determine the Java command to use to start the JVM.'

case ${1:-} in
    --check) check=yes ;;
    '')      check=no ;;
    *)       echo "build-prep: unknown option '$1' (only --check is understood)" >&2
             exit 2 ;;
esac

die () {
    echo "build-prep: $*" >&2
    exit 1
}

[ -f "$target_sh" ] || die "there is no gradlew here; run this from the project root"
[ -f "$target_bat" ] || die "there is no gradlew.bat here; run this from the project root"
[ -f "$frag_sh" ] || die "missing $frag_sh"
[ -f "$frag_bat" ] || die "missing $frag_bat"

sh_desired=$(mktemp) || die "mktemp failed"
bat_desired=$(mktemp) || die "mktemp failed"
trap 'rm -f "$sh_desired" "$bat_desired"' EXIT INT TERM

# --- gradlew ------------------------------------------------------------------
# Drop any block already there, then splice the current one in above the anchor.
# A blank line after the block is part of the region the block was written with, so
# dropping it too keeps repeated runs from stacking blank lines up.
awk -v block="$frag_sh" -v anchor="$anchor" '
    BEGIN { while ((getline line < block) > 0) blk[++n] = line; close(block) }
    /^# >>> zhusheng/ { inblock = 1 }
    inblock { if (/^# <<< zhusheng/) { inblock = 0; dropblank = 1 } next }
    dropblank { dropblank = 0; if ($0 == "") next }
    !placed && index($0, anchor) == 1 {
        for (i = 1; i <= n; i++) print blk[i]
        placed = 1
    }
    { print }
    END { if (!placed) exit 1 }
' "$target_sh" > "$sh_desired" ||
    die "could not find \"$anchor\" in gradlew; has the Gradle version changed?"

# --- gradlew.bat --------------------------------------------------------------
# The block is a self-contained chunk that sets JAVA_HOME and then falls through into
# Gradle's own script, so prepending it is enough. Line endings are normalised to the
# CRLF a batch file wants.
{
    awk '{ sub(/\r$/, ""); printf "%s\r\n", $0 }' "$frag_bat"
    awk '
        /^@rem >>> zhusheng/ { inblock = 1 }
        inblock { if (/^@rem <<< zhusheng/) { inblock = 0; dropblank = 1 } next }
        dropblank { dropblank = 0; if ($0 == "") next }
        { sub(/\r$/, ""); printf "%s\r\n", $0 }
    ' "$target_bat"
} > "$bat_desired"

# --- report and write ---------------------------------------------------------
sh_stale=no
bat_stale=no
cmp -s "$sh_desired" "$target_sh" || sh_stale=yes
cmp -s "$bat_desired" "$target_bat" || bat_stale=yes

if [ "$sh_stale" = no ] && [ "$bat_stale" = no ]; then
    echo "build-prep: gradlew and gradlew.bat already carry the JDK selection block."
else
    if [ "$check" = yes ]; then
        echo "build-prep: out of date:" >&2
        [ "$sh_stale" = yes ] && echo "build-prep:   gradlew" >&2
        [ "$bat_stale" = yes ] && echo "build-prep:   gradlew.bat" >&2
        exit 1
    fi
    # Write through the existing files rather than replacing them, so that gradlew
    # keeps its executable bit.
    if [ "$sh_stale" = yes ]; then
        cat "$sh_desired" > "$target_sh"
        echo "build-prep: re-applied the JDK selection block to gradlew"
    fi
    if [ "$bat_stale" = yes ]; then
        cat "$bat_desired" > "$target_bat"
        echo "build-prep: re-applied the JDK selection block to gradlew.bat"
    fi
fi

if grep -q '^[[:space:]]*org\.gradle\.java\.home' gradle.properties 2>/dev/null; then
    echo "build-prep: note: gradle.properties sets org.gradle.java.home, which outranks" >&2
    echo "           JAVA_HOME and so defeats the block above. Consider removing it." >&2
fi

exit 0
