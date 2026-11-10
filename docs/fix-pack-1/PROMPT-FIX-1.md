Bug fixes from my testing: scanning flow, auto-crop, crop screen

I tested the app on my phone. After taking a photo it doesn't auto-crop. The magnifier circle while cropping is just green, with no view of the photo. The crop screen layout is broken: the title is under the status bar and the Done button is squashed into an empty green bar. And tapping **Next** goes back or closes the app completely.

The folder `docs/fix-pack-1/` has the full fix instructions, pictures of how each screen must look, my screenshots of the bugs, and a reference page detector with test photos. If the files are attached to this message instead of already being in the repo, put them in `docs/fix-pack-1/` first (keep the folder structure) and commit.

1. Read `docs/fix-pack-1/FIXES-1.md` completely. For everything it covers, it **overrides** `docs/SPEC.md` and `docs/camera-pack/CAMERA-ADDON.md`.
2. Look at every picture in `docs/fix-pack-1/mockups/` (start with `00-fix-overview.png`) and my screenshots in `docs/fix-pack-1/owner-report/`. The fixed screens must look like the mockups.
3. Start with section 4: reproduce the "Next closes the app" bug, find the real cause in logcat, and write the cause in `CHANGELOG.md` before fixing it.
4. Auto-crop must be an exact Kotlin port of `docs/fix-pack-1/detection/reference_detect.py` (OpenCV), run on every captured or imported photo. Add the golden tests from section 2.3 and make all 7 pass. Don't tune numbers to pass; fix the port.
5. Build the new flow from section 1: Camera → Crop → Continue → Review & filter → Done → Document, with the scan session that survives Next, Back, rotation and the app being closed.
6. Add the local crash log (section 4.1) so I can send you crash reports.
7. No new permissions. The only library this needs is OpenCV, which is already allowed for the camera.
8. When done, build a new release, add the checks in section 8 to my test checklist, update `docs/PROGRESS.md`, and tell me in simple words what was wrong and what you changed.
