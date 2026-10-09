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
@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
