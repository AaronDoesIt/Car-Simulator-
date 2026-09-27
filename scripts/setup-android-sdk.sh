#!/usr/bin/env bash
# Installs the Android SDK pieces this project needs into $ANDROID_HOME.
# Safe to run repeatedly. Used as the cloud environment's setup script and
# runnable by hand on any Linux box with Java 17+ and curl.
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

mkdir -p "$ANDROID_HOME/cmdline-tools"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Downloading Android command-line tools..."
  curl -fsSL -o /tmp/cmdline-tools.zip "$CMDLINE_TOOLS_URL"
  rm -rf /tmp/cmdline-tools
  unzip -q -o /tmp/cmdline-tools.zip -d /tmp/cmdline-tools
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv /tmp/cmdline-tools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
  rm -f /tmp/cmdline-tools.zip
fi

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "Accepting SDK licenses..."
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true

echo "Installing platform 35 and build tools..."
sdkmanager --sdk_root="$ANDROID_HOME" --install \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null

# Make the SDK visible to later shells and to Gradle.
for rc in "$HOME/.bashrc" "$HOME/.profile"; do
  grep -q "ANDROID_HOME=" "$rc" 2>/dev/null || {
    echo "export ANDROID_HOME=\"$ANDROID_HOME\"" >> "$rc"
    echo "export PATH=\"\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH\"" >> "$rc"
  }
done
for d in "$PWD" /home/user/*/; do
  if [ -f "$d/settings.gradle.kts" ]; then
    echo "sdk.dir=$ANDROID_HOME" > "$d/local.properties"
  fi
done

echo "Android SDK ready at $ANDROID_HOME"
