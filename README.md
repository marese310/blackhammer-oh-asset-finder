# BlacHammer Once Human Asset Finder

A Windows PowerShell GUI for searching the included Once Human asset metadata. The interface displays the supplied BlacHammer logo, the BlacHammer name, and a link to the [BlacHammer Discord](https://discord.gg/qeGAX6VCUz).

## Requirements

- Windows with Windows PowerShell 5.1 or later.
- The application files kept together, including `assets\blachammer-logo.png`.

The app uses Windows Forms and System.Drawing from .NET. No package installation is required.

## Run from source

From this directory, run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\search_asset_gui.ps1"
```

The app reads `OnceHuman_EnhancedDatabase.csv` beside the script. Manual asset entries are stored separately under `%LOCALAPPDATA%\OnceHumanAssetExplorer\manual_items.csv`.

## Version 2.0 release

The complete branded v2.0 source and Windows release are in [`Release_v2.0`](./Release_v2.0/). It includes the PowerShell GUI source, the Windows executable, the BlacHammer logo, and the WebView2 files needed for embedded online maps. The database is embedded in the v2.0 PowerShell script, so no additional CSV is required for that release.

Run the v2.0 source from its directory with:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\OnceHumanAssetExplorer.ps1"
```

For embedded online maps, install the Microsoft Edge WebView2 Runtime. Refer to [`Release_v2.0/README.md`](./Release_v2.0/README.md) for v2.0 usage details.

## Android app

The native Android app, **BlacHammer Once Human Asset Finder**, is in [`android/`](./android/). It searches the bundled asset list offline, supports category and subcategory filters, shows asset details, and links to the BlacHammer Discord. On first launch it imports the included CSV into a local SQLite database; allow a short time for this one-time setup to finish. Once the Android build workflow finishes, download the APK from [GitHub Releases](https://github.com/marese310/blackhammer-oh-asset-finder/releases).

To build a debug-installable APK, install JDK 17, Android SDK platform 35, and build tools 34.0.0 and 35.0.0, then run these commands from the `android` directory:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The APK is created at `android\app\build\outputs\apk\debug\app-debug.apk`. Copy it to an Android phone and install it. Depending on Android settings, allow installation from the browser or file manager that opens the APK. The APK is debug-signed for testing and direct sideloading, not a Play Store release.

The GitHub Actions workflow can build an APK from the **Actions** tab; download it from the workflow run's artifacts. It requires explicitly confirming Google's Android SDK license agreement before the workflow installs the SDK. Review the agreement before selecting the confirmation. To also publish the APK as a GitHub Release, select the separate release option and enter a release tag, such as `android-v1.0.0`. Spaces in the tag are automatically changed to hyphens.

## Build a standalone PowerShell script

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\generate_standalone.ps1"
```

This generates `OnceHumanAssetExplorer_Standalone.ps1` with the CSV database embedded and copies the logo to `assets\blachammer-logo.png` beside the generated script. Keep that `assets` folder beside the standalone script. This build step creates a standalone `.ps1`, not a compiled `.exe`.

## Data

`OnceHuman_EnhancedDatabase.csv` is the app's searchable asset metadata. It can be replaced with another CSV using the same column layout. The app does not download game files or map data.

## Community

Feature requests and bug reports are welcome through [GitLab issues](https://gitlab.com/alpha-one-security-group/blackhammer-oh-asset-finder/-/issues). Join the [Discord community](https://discord.gg/qeGAX6VCUz).

## License and permissions

This repository intentionally has no license. Public visibility permits viewing the files only; it does not grant permission to build, use, copy, modify, or redistribute the source, logo, or database. Contact BlacHammer for permission before using or contributing code. If you want to grant broader reuse rights, add a suitable license before inviting public contributions.
