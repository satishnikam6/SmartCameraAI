# Smart Camera AI (starter build)

This is a starter Android project for a camera assistant. It uses CameraX, analyses preview brightness, makes simple exposure recommendations, and saves photos to the gallery.

## Important limitations
- This is a starter build, not a verified Pixel-specific release.
- It does not include AI scene recognition yet.
- It cannot control Google's separate Camera app.
- It does not implement Google's full HDR/Night Sight computational photography.
- Device capabilities and exposure behavior must be tested on the target phone.

## Build APK in GitHub Actions without writing code
1. Create/sign in to a GitHub account.
2. Create a new repository, e.g. `SmartCameraAI`.
3. Upload the contents of this ZIP to the repository (extract first; upload folders/files using GitHub's web UI).
4. Open the repository's **Actions** tab.
5. Select **Build Smart Camera AI APK** and click **Run workflow**.
6. When the run succeeds, open it and download the `SmartCameraAI-debug-apk` artifact.
7. Extract the downloaded artifact ZIP to get `app-debug.apk`.
8. Transfer the APK to the Pixel, open it, and allow installation from that source if Android prompts you. Only install APKs you trust.

If the workflow fails, open the failed run and inspect the build log. This project has not been built or device-tested in this environment.
