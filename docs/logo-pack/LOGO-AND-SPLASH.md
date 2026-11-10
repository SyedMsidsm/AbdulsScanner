# Abdul's Scanner — App icon and start-up animation

This adds to `docs/SPEC.md`. It replaces the app-icon description in section 8.2 with finished files, and adds an animated splash screen. The owner has approved the one new dependency listed below.

## The design

A white document with a folded corner, inside four white scanner-viewfinder corners, on the brand green `#13A06F`. A dark-green scan line (`#0B6E4A`) crosses the page.

**Start-up animation (1 second, Android 12 and newer):**

| Time | What happens |
| --- | --- |
| 0–350 ms | The four viewfinder corners snap in from slightly larger, with a small overshoot |
| 150–450 ms | The page grows into place and fades in |
| 380–520 ms | The folded corner appears |
| 450–800 ms | The scan line sweeps down the page; each text line appears as the scan line passes it |
| 800–1000 ms | The scan line settles in the middle: the final icon |
| then | The splash fades out (250 ms) while the icon grows slightly, revealing the Home screen |

Android 8–11 show the same icon without animation (the system doesn't support animated splash icons there).

See `preview/splash-animation.mp4` (or `.gif`), `preview/splash-keyframes.png` and `preview/launcher-shapes.png`.

## Files

```
logo/
  abdulsscanner-icon.svg              full icon (green rounded square), for README/docs
  abdulsscanner-icon-1024.png / -512 / -192
  abdulsscanner-icon-foreground.svg   artwork only (transparent)
  abdulsscanner-icon-monochrome.svg   one-colour version
android/res/
  drawable/ic_launcher_foreground.xml       adaptive icon foreground (vector)
  drawable/ic_launcher_background.xml       solid @color/brand_green
  drawable/ic_launcher_monochrome.xml       themed icon (Android 13+)
  mipmap-anydpi-v26/ic_launcher.xml         adaptive icon
  mipmap-anydpi-v26/ic_launcher_round.xml   adaptive icon (round)
  drawable/splash_icon.xml                  static splash icon (Android 8–11)
  drawable-v31/splash_icon_animated.xml     animated splash icon (Android 12+)
  anim/splash_overshoot.xml                 interpolator used by the animation
  values/splash_colors.xml                  brand_green, splash_background
  values/splash_theme.xml                   Theme.AbdulsScanner.Starting (static)
  values-v31/splash_theme.xml               Theme.AbdulsScanner.Starting (animated)
preview/                                    for humans only; do not put in the app
```

All artwork fits inside the adaptive-icon safe zone (66 dp circle on the 108 dp canvas), so no launcher shape clips it.

## Instructions for the builder

1. **Copy** everything under `android/res/` into `app/src/main/res/` (same folder names). Copy `logo/` to `docs/logo/`. Do not copy `preview/`.
   - If `colors.xml` already defines `brand_green`, keep one definition (value `#13A06F`).
   - Delete any default launcher icons the project template created (`mipmap-*/ic_launcher*.png` / `.webp`, the template `ic_launcher_foreground`/`background`), so only these are used.
2. **Dependency** (approved by the owner; add to `libs.versions.toml`):
   `androidx.core:core-splashscreen:1.2.0`
3. **Manifest:**
   - `<application android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round" …>`
   - `MainActivity`: `android:theme="@style/Theme.AbdulsScanner.Starting"`.
   - `Theme.AbdulsScanner` (the app's normal theme from section 8) stays the `postSplashScreenTheme`.
4. **MainActivity:**

   ```kotlin
   override fun onCreate(savedInstanceState: Bundle?) {
       val splash = installSplashScreen()          // must be before super.onCreate
       super.onCreate(savedInstanceState)

       // Keep the splash only until the first Home data is ready (never longer than 2 s).
       val startedAt = SystemClock.uptimeMillis()
       splash.setKeepOnScreenCondition {
           !appReady.value && SystemClock.uptimeMillis() - startedAt < 2000
       }

       // Let the 1-second icon animation finish, then fade out with a slight zoom.
       splash.setOnExitAnimationListener { provider ->
           val remaining = if (Build.VERSION.SDK_INT >= 31)
               (provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis
                   - SystemClock.uptimeMillis()).coerceIn(0L, 1000L)
           else 0L
           provider.iconView.animate().scaleX(1.12f).scaleY(1.12f)
               .setStartDelay(remaining).setDuration(250).start()
           provider.view.animate().alpha(0f)
               .setStartDelay(remaining).setDuration(250)
               .withEndAction { provider.remove() }.start()
       }
       setContent { /* app */ }
   }
   ```

   `appReady` is a `StateFlow<Boolean>` that becomes `true` when the Home list's first database result arrives.
5. **Rules:**
   - No extra waiting: the splash shows only on a cold start, for about 1–1.25 s on Android 12+, and only as long as loading takes on Android 8–11.
   - Don't redraw or "improve" the vectors. If a build error points at one of them, fix only that error and say so in `CHANGELOG.md`.
   - Use `docs/logo/abdulsscanner-icon-512.png` at the top of `README.md`, and the icon in Settings → About.
6. **Check:**
   - Cold start on an Android 12+ emulator: the animation plays once, smoothly, then fades into Home.
   - Cold start on Android 8–11: static icon on green, then Home.
   - The launcher icon looks right as a circle, squircle and rounded square, and as a themed icon on Android 13+ (Settings → Wallpaper & style → Themed icons).
   - Add these to the owner's final test checklist.
