# Rename the app to "Abul's Scanner"

**Date:** 2026-11-10 · Adds to `docs/SPEC.md` and the add-on packs. Where this file disagrees with them about the app's **name**, this file wins.

The app's name changes from **Abdul's Scanner** to **Abul's Scanner** (with a space, apostrophe before the "s").

## Prompt to paste to the builder

```
Rename the app's visible name from "Abdul's Scanner" to "Abul's Scanner".
Follow docs/RENAME-TO-ABULS-SCANNER.md exactly (put it at that path if it's attached instead).
Change only what its "Change" list says. Never change anything in its "Do NOT change" list:
those are internal IDs, and changing them would break updates or lose my data.
Do it now if the app is already built, otherwise build it with the new name from the start.
Add the checks at the end of the file to my final test checklist and note the rename in CHANGELOG.md.
```

---

## 1. Change (everything the owner can see)

| Where | Old | New |
| --- | --- | --- |
| App name under the icon, recent-apps title (`res/values/strings.xml` → `app_name`) | Abdul's Scanner | `Abul\'s Scanner` (escape the apostrophe in XML) |
| Home screen top-bar title | Abdul's Scanner | Abul's Scanner |
| Settings → About, licences screen, notifications (e.g. backup failed), share-sheet titles, any other on-screen text | Abdul's Scanner | Abul's Scanner |
| Phone folder for PDF copies (spec 6.2) | `Documents/Abdul's Scanner/` | `Documents/Abul's Scanner/` |
| PDF-tool outputs (spec 11.11) | `Documents/Abdul's Scanner/Tools/` | `Documents/Abul's Scanner/Tools/` |
| Saved images (PDF → JPG, Save JPGs) | `Pictures/Abdul's Scanner/` | `Pictures/Abul's Scanner/` |
| PDF metadata "Producer" (spec 11.5) | Abdul's Scanner | Abul's Scanner |
| Backup file names (spec 12.1) | `AbdulsScanner-backup-yyyyMMdd-HHmm.zip` | `AbulsScanner-backup-yyyyMMdd-HHmm.zip` |
| Release APK file name (spec 15.3) | `AbdulsScanner-{version}.apk` | `AbulsScanner-{version}.apk` |
| `README.md` title and install steps, GitHub Release titles | Abdul's Scanner | Abul's Scanner |
| `docs/SPEC.md` | add one line to section 1.3: "App name: Abul's Scanner (renamed from Abdul's Scanner on 2026-11-10; internal IDs unchanged)" | |

Search the code and resources for **visible** uses of "Abdul" and change them case by case. Do **not** run a blind find-and-replace across the project.

## 2. Do NOT change (internal IDs)

| Keep exactly as it is | Why |
| --- | --- |
| `applicationId` / `namespace` `com.syedmsidsm.abdulsscanner` and the Kotlin package | Android treats a new ID as a different app: no update, data lost |
| Release signing key and alias | Same reason |
| Database name `abdulsscanner.db`, DataStore file names, WorkManager unique work names | Renaming loses saved data or scheduled backups |
| FileProvider authority (`${applicationId}.fileprovider`) | Follows the application ID |
| Backup `manifest.json` `"format": "abdulsscanner-backup"` | Old backups must keep restoring |
| Theme/style names (`Theme.AbdulsScanner`, `Theme.AbdulsScanner.Starting`), resource file names, class names | Internal only; renaming adds risk and changes nothing the owner sees |
| GitHub repository name `SyedMsidsm/AbdulsScanner` | Not visible in the app; links and CI keep working |
| The app icon and start-up animation (logo pack) | They contain no text |

## 3. Keep existing data working (if a build with the old name is already installed)

Do this once, on the first start after the update (store a flag `renamedToAbul = true` in DataStore when done):

1. **PDF copies folder:** for each document with a `mirrorUri`, try to move the file to the new folder (API 29+: `contentResolver.update(uri, ContentValues { RELATIVE_PATH = "Documents/Abul's Scanner/" }, null, null)`). If moving fails (for example, the app no longer owns the file), write a fresh copy into the new folder and update `mirrorUri`. Never delete the old file if the new copy could not be written. Do the same for files in the old `Tools/` folder that the app owns. On Android 8–9, move files with normal file operations.
2. **Backups:** restore must accept files named with either prefix (`AbdulsScanner-backup-…` or `AbulsScanner-backup-…`). The automatic-backup "keep last 3" clean-up must count and rotate files with **both** prefixes in the chosen folder, so old backups don't pile up forever.
3. **Pictures folder:** leave old images where they are; new ones go to `Pictures/Abul's Scanner/`.
4. If the empty old folder `Documents/Abdul's Scanner/` remains and the app can remove it, remove it; otherwise leave it.

## 4. Mockups

The mockup images still show "Abdul's Scanner" in the Home top bar and on a few screens. Use **Abul's Scanner** wherever a mockup shows the old name. Everything else in the mockups is unchanged.

## 5. Checks (add to the owner's final test checklist)

1. The name under the app icon and on the Home screen reads **Abul's Scanner** (apostrophe shown correctly, not `Abul\'s`).
2. Updating over an older build keeps every document, tag, setting and backup schedule.
3. New PDF copies appear in `Documents/Abul's Scanner`, and older copies have moved there (or been recreated there).
4. A backup made before the rename (`AbdulsScanner-backup-…zip`) still restores.
5. Automatic backup keeps only the 3 newest files, counting old- and new-named ones together.
6. The release APK file is named `AbulsScanner-x.y.z.apk`.
