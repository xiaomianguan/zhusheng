@echo off
@rem build-prep.bat - re-apply the zhusheng JDK-selection block to a regenerated
@rem gradlew.bat.
@rem
@rem Why this exists: the build needs a JDK that the Groovy compiler bundled with
@rem Gradle 8.7 can read (17 or 21), and that cannot be arranged from
@rem gradle.properties on a machine whose default java is newer. So gradlew.bat
@rem picks a JDK itself, in a block that Gradle's wrapper task does not know about
@rem and throws away whenever it regenerates the wrapper. Run this afterwards to put
@rem the block back.
@rem
@rem The block itself lives in tools\gradlew-jdk17.bat, so that this file and
@rem build-prep.sh share one copy. It is a self-contained chunk that sets JAVA_HOME
@rem and then falls through into the script Gradle generated, which is why it can be
@rem prepended rather than spliced in.
@rem
@rem This file only handles gradlew.bat, the wrapper Windows uses. The POSIX gradlew
@rem is patched by build-prep.sh; run that under Git Bash or WSL if you need it.

setlocal
set "HERE=%~dp0"
set "TARGET=%HERE%gradlew.bat"
set "FRAGMENT=%HERE%tools\gradlew-jdk17.bat"

if not exist "%TARGET%" (
    echo build-prep: there is no gradlew.bat here; run this from the project root. 1>&2
    exit /b 1
)
if not exist "%FRAGMENT%" (
    echo build-prep: missing %FRAGMENT% 1>&2
    exit /b 1
)

@rem Already done? The block, when it is there, is the first line of the file.
findstr /b /c:"@rem >>> zhusheng" "%TARGET%" >nul 2>&1
if not errorlevel 1 (
    echo build-prep: gradlew.bat already carries the JDK selection block.
    goto :note
)

@rem Copy the block and the wrapper into one file, then move it back into place.
@rem /b keeps copy from treating the files as text, and the move is what makes the
@rem replacement atomic enough that an interrupted run cannot leave half a wrapper.
if exist "%TARGET%.new" del "%TARGET%.new"
copy /b /y "%FRAGMENT%" + "%TARGET%" "%TARGET%.new" >nul
if errorlevel 1 (
    echo build-prep: could not write the patched gradlew.bat. 1>&2
    if exist "%TARGET%.new" del "%TARGET%.new"
    exit /b 1
)
move /y "%TARGET%.new" "%TARGET%" >nul
if errorlevel 1 (
    echo build-prep: could not replace gradlew.bat. 1>&2
    exit /b 1
)
echo build-prep: re-applied the JDK selection block to gradlew.bat.

:note
@rem The block only helps if nothing overrides it: org.gradle.java.home outranks JAVA_HOME.
findstr /r /c:"^org.gradle.java.home" "%HERE%gradle.properties" >nul 2>&1
if not errorlevel 1 (
    echo build-prep: note: gradle.properties sets org.gradle.java.home, which outranks 1>&2
    echo            JAVA_HOME and so defeats the block above. Consider removing it. 1>&2
)

exit /b 0
