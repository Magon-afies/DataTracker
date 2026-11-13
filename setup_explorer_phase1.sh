#!/bin/bash
echo "🚀 Starting Data Tracker Explorer Upgrade (Phase 1 Base)..."

# 1. Safety check
if [ ! -f "build.gradle.kts" ]; then
    echo "❌ Error: Run this from the root of the DataTracker repository!"
    exit 1
fi

# 2. Find the correct package path dynamically
PKG_PATH=$(find app/src/main/java -type d -name "datatracker" | head -n 1)
if [ -z "$PKG_PATH" ]; then
    echo "❌ Error: Could not find 'datatracker' package directory."
    exit 1
fi
echo "✅ Found package path: $PKG_PATH"

# 3. Create new phase directories (Ground Rule: Keep existing code style)
mkdir -p "$PKG_PATH/explorer"
mkdir -p "$PKG_PATH/archive"
mkdir -p "$PKG_PATH/cleaner2"
mkdir -p "$PKG_PATH/vault"
mkdir -p "$PKG_PATH/transfer"
echo "✅ Created new package directories."

# 4. Bump versionName to 4.0
sed -i 's/versionName = "[^"]*"/versionName = "4.0"/' app/build.gradle.kts
sed -i 's/versionName="[0-9.]*"/versionName="4.0"/' app/build.gradle.kts
echo "✅ Bumped versionName to 4.0"

# 5. Add dependencies to app/build.gradle.kts (if not already present)
DEPS_FILE="app/build.gradle.kts"
if ! grep -q "zip4j" "$DEPS_FILE"; then
    echo "Adding new dependencies to build.gradle.kts..."
    # Create a temp file with new dependencies
    cat << 'DEPS' > /tmp/new_deps.txt
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("org.tukaani:xz:1.9")
    implementation("com.github.junrar:junrar:7.5.5")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("io.coil-kt:coil:2.5.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
DEPS
    # Insert them after the first 'dependencies {' block
    awk '/dependencies \{/ {print; system("cat /tmp/new_deps.txt"); next}1' "$DEPS_FILE" > /tmp/temp_gradle.kts
    mv /tmp/temp_gradle.kts "$DEPS_FILE"
    echo "✅ Added new dependencies."
fi

# 6. Ensure MANAGE_EXTERNAL_STORAGE is in AndroidManifest.xml
MANIFEST="app/src/main/AndroidManifest.xml"
if ! grep -q "MANAGE_EXTERNAL_STORAGE" "$MANIFEST"; then
    sed -i '/<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"\/>/a\    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />' "$MANIFEST"
    echo "✅ Added MANAGE_EXTERNAL_STORAGE to AndroidManifest.xml"
fi

# 7. Create placeholder FilesFragment.kt
cat << 'KOTLIN' > "$PKG_PATH/explorer/FilesFragment.kt"
package com.crestpoint.datatracker.explorer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment

// TODO: Phase 1 - Implement Core Explorer UI and Logic
class FilesFragment : Fragment(R.layout.fragment_files) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: Wire up storage cards, breadcrumbs, and file list
    }
}
KOTLIN
echo "✅ Created placeholder FilesFragment.kt"

# 8. Create placeholder fragment_files.xml
mkdir -p app/src/main/res/layout
cat << 'XML' > app/src/main/res/layout/fragment_files.xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Files Explorer (Phase 1 WIP)"
        android:textSize="20sp"
        android:textStyle="bold"/>
    <!-- TODO: Add storage cards, breadcrumbs, and RecyclerView -->
</LinearLayout>
XML
echo "✅ Created placeholder fragment_files.xml"

echo "🎉 Base scaffolding complete! Ready for code edits."
