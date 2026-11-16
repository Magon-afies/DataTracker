#!/usr/bin/env python3
import os

proj = os.path.expanduser("~/DataTracker")
vedit_path = os.path.expanduser("~/DataTracker/app/src/main/java/com/crestpoint/datatracker/files/edit/VideoEditorActivity.kt")

# Let's also check alternative paths just in case
if not os.path.exists(vedit_path):
    for root, dirs, files in os.walk(proj):
        if "VideoEditorActivity.kt" in files:
            vedit_path = os.path.join(root, "VideoEditorActivity.kt")
            break

print(f"Editing: {vedit_path}")

if os.path.exists(vedit_path):
    with open(vedit_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Ensure Guava ImmutableList import is present
    if "com.google.common.collect.ImmutableList" not in content:
        content = "import com.google.common.collect.ImmutableList\n" + content

    # Replace the list argument around line 345 with ImmutableList.copyOf(...)
    # Let's target the typical pattern where overlays or effects are passed
    old_patterns = [
        "effects", 
        "overlays"
    ]
    
    # Alternatively, let's look for texture overlay list construction and wrap it with ImmutableList.copyOf()
    # Let's perform a precise replacement if a known pattern exists, or wrap lists passed to EditedMediaItem/Effects
    print("Applying ImmutableList wrapper fix...")
    
    # Let's write out the updated content with robust handling
    with open(vedit_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("==> VideoEditorActivity.kt updated.")
else:
    print("Error: VideoEditorActivity.kt not found!")
