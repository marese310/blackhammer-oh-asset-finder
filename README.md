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
