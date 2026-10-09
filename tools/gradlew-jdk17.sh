# >>> zhusheng: JDK selection (generated from tools/gradlew-jdk17.sh by build-prep.sh; edit that file, not this block)
##############################################################################
#
#   zhusheng: use a JDK this Gradle can actually run on.
#
#   Gradle 8.7's bundled Groovy compiler cannot read the class files of a JDK newer
#   than it knows: a build script that names any JDK type - even the Boolean in
#   `buildFeatures { buildConfig true }` - aborts with "Unsupported class file major
#   version 70" when the machine's default `java` is, say, JDK 26. So rather than
#   pinning one machine's path in gradle.properties, look for a JDK this project
#   supports (17 to 21, 17 first) and build with that. A JAVA_HOME that already names
#   a suitable JDK is left alone, so a deliberate choice always wins.
#
#   Note for maintainers: Gradle generates this file, so running the `wrapper` task
#   replaces this block. Re-apply it afterwards.
#
##############################################################################

# The major version of the JDK at $1, or nothing when there is no JDK there.
zs_java_major () {
    [ -n "$1" ] && [ -x "$1/bin/java" ] || return 1
    "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1
}

# True when $1 is a JDK of a version this Gradle can run on.
zs_jdk_ok () {
    zs_major=$( zs_java_major "$1" ) || return 1
    [ -n "$zs_major" ] || return 1
    [ "$zs_major" -ge 17 ] 2>/dev/null || return 1
    [ "$zs_major" -le 21 ] 2>/dev/null || return 1
}

if ! zs_jdk_ok "$JAVA_HOME" ; then
    zs_jdk=
    # The java_home lookups are the canonical way on macOS; the rest are where
    # Homebrew, the JDK installers and the Linux package managers leave JDKs. A
    # pattern that matches nothing stays literal, and zs_jdk_ok turns it down.
    for zs_candidate in \
        "$( /usr/libexec/java_home -v 17 2>/dev/null )" \
        /opt/homebrew/opt/openjdk@17 \
        /usr/local/opt/openjdk@17 \
        /Library/Java/JavaVirtualMachines/*17*/Contents/Home \
        /usr/lib/jvm/java-17-openjdk* \
        /usr/lib/jvm/*17*/ \
        "$( /usr/libexec/java_home -v 21 2>/dev/null )" \
        /opt/homebrew/opt/openjdk@21 \
        /usr/local/opt/openjdk@21 \
        /Library/Java/JavaVirtualMachines/*21*/Contents/Home \
        /usr/lib/jvm/java-21-openjdk* \
        /usr/lib/jvm/*21*/ \
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ; do
        if zs_jdk_ok "$zs_candidate" ; then
            zs_jdk=$zs_candidate
            break
        fi
    done

    if [ -n "$zs_jdk" ] ; then
        JAVA_HOME=$zs_jdk
        export JAVA_HOME
        warn "zhusheng: building with Java $( zs_java_major "$JAVA_HOME" ) ($JAVA_HOME)"
    else
        warn "zhusheng: found no JDK 17 to 21; using ${JAVA_HOME:-the java on your PATH}."
        warn "          If the build fails with 'Unsupported class file major version',"
        warn "          install JDK 17 (or 21) or set JAVA_HOME to one."
    fi
fi
# <<< zhusheng: JDK selection

