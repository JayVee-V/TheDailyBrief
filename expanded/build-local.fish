#!/usr/bin/env fish
set -l here (dirname (status --current-filename))
cd "$here"

# Android Studio on Linux normally puts the SDK here.
if not set -q ANDROID_HOME; and test -d "$HOME/Android/Sdk"
    set -gx ANDROID_HOME "$HOME/Android/Sdk"
end
if set -q ANDROID_HOME; and test -d "$ANDROID_HOME"
    printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
end

set -l runner ""
if type -q gradle
    set runner (command -v gradle)
else
    # Reuse any Gradle distribution Android Studio/another project has already cached.
    set -l cached (find "$HOME/.gradle/wrapper/dists" -type f -path '*/bin/gradle' 2>/dev/null | sort -V | tail -n 1)
    if test -n "$cached"; and test -x "$cached"
        set runner "$cached"
    else if test -x ./gradlew
        set runner ./gradlew
    end
end

if test -z "$runner"
    echo "No Gradle runner found. Open this folder in Android Studio and choose Build > Build APK(s)."
    exit 1
end

echo "Using: $runner"
$runner :app:assembleDebug
or exit $status

set -l apk "$here/app/build/outputs/apk/debug/app-debug.apk"
if test -f "$apk"
    echo
    echo "APK: $apk"
end
