@echo off
REM Build ringan tanpa Android Studio. Butuh: JDK 17 + Android SDK cmdline + Gradle.
REM Isi ANDROID_HOME otomatis dari %LOCALAPPDATA%\Android\Sdk kalau ada.
if "%ANDROID_HOME%"=="" set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
if "%ANDROID_SDK_ROOT%"=="" set ANDROID_SDK_ROOT=%ANDROID_HOME%
echo ANDROID_HOME=%ANDROID_HOME%
call gradle assembleDebug --warning-mode all
if %ERRORLEVEL%==0 echo APK jadi di: app\build\outputs\apk\debug\app-debug.apk
