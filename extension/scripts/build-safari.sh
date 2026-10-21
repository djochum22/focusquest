#!/usr/bin/env bash
# Wraps dist-safari/ (node scripts/build.mjs --safari) in "FocusQuest for Safari", the small Mac app
# Safari needs an extension to come in. Open the app once; Safari then lists the extension.
# Needs Xcode (not only the Command Line Tools). Run through `npm run build:safari`.
#
# Signing: with the free certificate of the Apple ID added in Xcode (Settings, Accounts), Safari keeps
# the extension on across restarts. Without one the app is signed for this Mac only, and Safari shows
# the extension only while Develop, "Allow Unsigned Extensions" is on, which it turns off at each start.
# FOCUSQUEST_TEAM picks the team; otherwise the first one Xcode's preferences list, or else the team of
# an "Apple Development" certificate in the keychain (Xcode 27 saves the team only once one exists).
set -euo pipefail

extension="$(cd "$(dirname "$0")/.." && pwd)"
out="$extension/safari-build"
app_name="FocusQuest for Safari"
bundle_id="com.focusquest.safari"

if ! xcrun --find safari-web-extension-converter >/dev/null 2>&1; then
  echo "Safari's converter is missing: install Xcode, then run sudo xcode-select -s /Applications/Xcode.app" >&2
  exit 1
fi
[[ -f "$extension/dist-safari/manifest.json" ]] || { echo "Build dist-safari/ first: npm run build:safari" >&2; exit 1; }

rm -rf "$out"
mkdir -p "$out"
xcrun safari-web-extension-converter "$extension/dist-safari" --project-location "$out" --app-name "$app_name" \
  --bundle-identifier "$bundle_id" --macos-only --copy-resources --no-open --no-prompt --force >/dev/null

# The converter names the app's id after the app, but the extension's after the id given; an embedded
# extension's id must start with its app's.
project="$out/$app_name/$app_name.xcodeproj/project.pbxproj"
sed -i '' -E "s/PRODUCT_BUNDLE_IDENTIFIER = \"?com\.focusquest\.[A-Za-z-]+\"?;/PRODUCT_BUNDLE_IDENTIFIER = $bundle_id;/" "$project"

# The team of an Apple ID added in Xcode, if any.
team="${FOCUSQUEST_TEAM:-$(defaults export com.apple.dt.Xcode - 2>/dev/null | python3 -c '
import plistlib, sys
def teams(value):
    if isinstance(value, dict):
        if isinstance(value.get("teamID"), str):
            yield value["teamID"]
        for inner in value.values():
            yield from teams(inner)
    elif isinstance(value, list):
        for inner in value:
            yield from teams(inner)
try:
    print(next(teams(plistlib.loads(sys.stdin.buffer.read())), ""))
except Exception:
    print("")
')}"

# The certificate's organisational unit is its team.
if [[ -z "$team" ]]; then
  team="$(security find-certificate -c "Apple Development" -p 2>/dev/null \
    | openssl x509 -noout -subject -nameopt multiline 2>/dev/null \
    | sed -n -E 's/^ *organizationalUnitName *= *([A-Z0-9]+)$/\1/p' | head -1)"
fi

if [[ -n "$team" ]]; then
  echo "Signing with team $team (Apple ID in Xcode)."
  signing=(CODE_SIGN_STYLE=Automatic "DEVELOPMENT_TEAM=$team" -allowProvisioningUpdates)
else
  echo "No Apple ID team found: signing for this Mac only. Safari needs Develop, \"Allow Unsigned Extensions\" after each start."
  echo "To sign: Xcode, Settings, Accounts, select the Apple ID, Manage Certificates, +, Apple Development; then build again."
  signing=(CODE_SIGN_STYLE=Manual CODE_SIGN_IDENTITY=- DEVELOPMENT_TEAM=)
fi

(cd "$out/$app_name" && xcodebuild -quiet -scheme "$app_name" -configuration Release -derivedDataPath "$out/derived" \
  MACOSX_DEPLOYMENT_TARGET=14.0 "${signing[@]}" build)
cp -R "$out/derived/Build/Products/Release/$app_name.app" "$out/"

echo "Built $out/$app_name.app. Open it once, then turn the extension on in Safari, Settings, Extensions."
