# Once Human Asset Explorer v2.0

Version 2.0 includes the bundled asset database and adds persistent appearance settings, external map references, and a user-managed map and marker workspace.

## Launch

Run `OnceHumanAssetExplorer.exe` for the Windows GUI, or run `OnceHumanAssetExplorer.ps1` with Windows PowerShell 5.1 or later. Keep the included `WebView2` folder beside the application to display online maps. Windows must also have the Microsoft Edge WebView2 Runtime installed:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\OnceHumanAssetExplorer.ps1"
```

The PowerShell release contains the database; no project files or adjacent CSV are required.

The application displays the BlacHammer logo from `assets\blachammer-logo.png` in the Asset Search tab, along with a link to the [BlacHammer Discord](https://discord.gg/qeGAX6VCUz). Keep the `assets` folder beside the script or executable.

## Asset search

- Search and filter the bundled asset database.
- Review asset details, copy names, and export search results to CSV.
- Open a related search on OnceHumanDB from an asset's details.
- Add, edit, delete, and undo deletion of manual asset records.

OnceHumanDB links open the website in the default browser. The app links to external site content; it does not download, scrape, or bundle website data.

## Map and markers

Open the **Map and Markers** tab to import a map image for a map, scenario, or game version. Import a locally available image using **Import Map Image**, or drag an image onto the map canvas. Supported formats are PNG, JPG/JPEG, BMP, GIF, and TIFF.

After importing, scroll to zoom, drag to pan, or use the zoom and **Fit Map** controls. Select **Place Marker**, click a location on the map, and enter a name, type/status, and notes. Click a saved pin to select it; use the marker search box to filter pins by name, type, or notes. Marker notes can be viewed from the list, and markers can be deleted.

Imported map images and locally saved pins/notes are available offline. Images are stored under `%LOCALAPPDATA%\OnceHumanAssetExplorer\Maps\`; users must import maps they have permission to use. The app does not extract or bundle third-party map tiles or pins. **MapGenie Online**, **TH Maps Online**, and **OnceHumanDB Map** load their map pages inside the Map and Markers tab using the Microsoft Edge WebView2 Runtime. Use **Offline Map** to return to your imported image and saved local markers. Online maps require an internet connection and remain hosted by their respective websites; the app does not download or synchronize site map data.

Each imported map is labeled with a name and a scenario/version, so users can keep separate map images for different scenarios or game versions. Markers are stored as normalized positions relative to the image and remain aligned when the window is resized.

## Appearance

Use **Appearance** in the Asset Search tab to customize the window background, text, accent color, font size, and window opacity. These settings are saved for the current Windows user.

## Local data

User-created data is stored under:

```text
%LOCALAPPDATA%\OnceHumanAssetExplorer\
```

- `manual_items.csv` — manual asset records.
- `theme.json` — appearance settings.
- `Maps\` — imported map images.
- `maps.csv` — map names, scenario/version labels, and image references.
- `map_markers.csv` — marker positions, names, types, and notes.
- `WebView2\` beside the application — Microsoft WebView2 SDK files needed for embedded online maps (redistribution terms are in `WebView2\LICENSE.txt`).
- `WebView2\` under this user-data folder — browser profile data used by the embedded online view.

Back up this folder to preserve user data. The bundled asset database remains read-only and separate from user-created records.
