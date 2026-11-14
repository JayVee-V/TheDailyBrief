#!/usr/bin/env fish
set -l here (dirname (status --current-filename))
fish "$here/build-local.fish"
or exit $status

set -l apk "$here/app/build/outputs/apk/debug/app-debug.apk"
if not test -f "$apk"
    echo "Build finished but APK was not found at $apk"
    exit 1
end

if type -q adb
    adb install -r "$apk"
else if test -x "$HOME/Android/Sdk/platform-tools/adb"
    "$HOME/Android/Sdk/platform-tools/adb" install -r "$apk"
else
    echo "APK built successfully: $apk"
    echo "ADB is not on PATH, so copy/install the APK manually."
end
