@rem >>> zhusheng: JDK selection (generated from tools\gradlew-jdk17.bat by build-prep.bat; edit that file, not this block)
@if "%DEBUG%"=="" @echo off
setlocal
@rem ---------------------------------------------------------------------------
@rem zhusheng: use a JDK this Gradle can actually run on.
@rem
@rem Gradle 8.7's bundled Groovy compiler cannot read the class files of a JDK newer
@rem than it knows: a build script that names any JDK type - even the Boolean in
@rem `buildFeatures { buildConfig true }` - aborts with "Unsupported class file major
@rem version 70" when the machine's default java is, say, JDK 26. So rather than
@rem pinning one machine's path in gradle.properties, look for a JDK this project
@rem supports (17, then 21) and build with that. A JAVA_HOME that is already set wins,
@rem so a deliberate choice always stands. (The `gradlew` script does the same for
@rem macOS and Linux.)
@rem
@rem Note for maintainers: Gradle generates this file, so running the `wrapper` task
@rem replaces this block. Re-apply it afterwards.
@rem ---------------------------------------------------------------------------

if defined JAVA_HOME goto zsJdkDone

set "ZS_JDK="
@rem A match on one of these patterns is a JDK of that version, so the directory name
@rem is all the version check that is needed.
for /d %%d in (
    "%ProgramFiles%\Eclipse Adoptium\jdk-17*"
    "%ProgramFiles%\Java\jdk-17*"
    "%ProgramFiles%\Microsoft\jdk-17*"
    "%ProgramFiles%\Zulu\zulu-17*"
    "%ProgramFiles%\BellSoft\LibericaJDK-17*"
    "%ProgramFiles%\Amazon Corretto\jdk17*"
    "%ProgramFiles%\AdoptOpenJDK\jdk-17*"
    "%LocalAppData%\Programs\Eclipse Adoptium\jdk-17*"
    "%LocalAppData%\Programs\Microsoft\jdk-17*"
) do if not defined ZS_JDK if exist "%%~fd\bin\java.exe" set "ZS_JDK=%%~fd"
if not defined ZS_JDK for /d %%d in (
    "%ProgramFiles%\Eclipse Adoptium\jdk-21*"
    "%ProgramFiles%\Java\jdk-21*"
    "%ProgramFiles%\Microsoft\jdk-21*"
    "%ProgramFiles%\Zulu\zulu-21*"
    "%ProgramFiles%\Android\Android Studio\jbr"
) do if not defined ZS_JDK if exist "%%~fd\bin\java.exe" set "ZS_JDK=%%~fd"

if defined ZS_JDK (
    set "JAVA_HOME=%ZS_JDK%"
    echo zhusheng: building with the JDK at %ZS_JDK% 1>&2
) else (
    echo zhusheng: found no JDK 17 or 21; using the java on your PATH. 1>&2
    echo           If the build fails with "Unsupported class file major version", 1>&2
    echo           install JDK 17 ^(or 21^) or set JAVA_HOME to one. 1>&2
)

:zsJdkDone
@rem <<< zhusheng: JDK selection
