# MedTraveler — Belarusian redesign + medication scan (OCR → active ingredient)

Date: 2026-07-11
Status: Approved design, pending spec review

## Goal

Two coordinated workstreams on the existing MedTraveler Android app (Java, min SDK 24,
target 36, Firebase Auth/Firestore, Room cache, OkHttp):

1. **Full "Vyshyvanka" redesign** — a modern Belarusian visual identity (white background,
   red accent, diamond folk-ornament decor) applied across all seven screens.
2. **Scan feature** — the user photographs a medicine box; the app OCRs the label with
   Google ML Kit (on-device), resolves the **active ingredient** via the OpenFDA Drug Label
   API, warns if that substance is restricted in the user's chosen destination countries,
   and saves it to the user's personal med list ("meds I take").

Non-negotiables kept from current app: Activity-per-screen structure, `BaseActivity`
drawer/toolbar, Firebase Auth gate for saving, 5 locales (en/es/be/uk/zh), OkHttp for
network, no debug-signed release fallback.

## Workstream 1 — Vyshyvanka visual system

### Palette (`res/values/colors.xml` rewrite)
- Background: white `#FFFFFF`, off-white surface `#FBF7F4`, surface variant `#F2ECE8`.
- Primary red `#C8102E`, primary dark `#A50D26`, accent `#C8102E`.
- Text: primary `#1A1A1A`, secondary `#5C5C5C`, on-primary `#FFFFFF`.
- Status semantics preserved: allowed green `#2E7D46`, restricted amber `#C77800`,
  penal deep red `#8E1420`.
- Quote/highlight recolored to the red-on-white family.

### Ornament decor (vector only, no raster)
- `res/drawable/ornament_band.xml` — tileable diamond/rhombus folk band, drawn in SVG
  paths. Used as a thin strip directly under the toolbar and inside `nav_header.xml`.
- `res/drawable/ornament_watermark.xml` — a larger single motif shown as a subtle
  watermark on `WelcomeActivity`.
- Everything is `VectorDrawable` so it scales cleanly and adds no APK weight.

### Theme (`res/values/themes.xml`)
- Toolbar stays **dark** (`toolbar_background`) with the red ornament band beneath it
  (decided: higher contrast, minimal status-bar rework).
- `MaterialCardView` with rounded corners for list/detail/result cards.
- `SectionHeader` style recolored to primary red.
- Status bar remains dark; `windowLightStatusBar=false`.

### Screens redesigned (all 7)
Welcome, MedicineList, MedicineDetail, Search, Auth, Profile, **Scan (new)**. Layout XML
updated to the new cards/typography/ornament; no behavioral change to existing flows
beyond the new scan entry points.

## Workstream 2 — Scan pipeline (Approach A: dedicated ScanActivity)

### Entry points
- Camera icon in the `MedicineListActivity` toolbar.
- "Scan" item in the drawer menu (`drawer_menu.xml`).
- Saving requires a signed-in user → reuse the existing redirect to `AuthActivity`.

### `ScanActivity` — single screen, four states
1. **Empty** — "Take photo" and "Pick from gallery" buttons + hint "point at the package".
2. **Recognizing** — photo preview + progress (OCR, then OpenFDA lookup).
3. **Result** — card with editable **brand** field (prefilled from OCR candidate) and the
   resolved **active ingredient**. If that substance matches the restriction catalog for
   the user's selected countries, show a red warning banner linking to
   `MedicineDetailActivity`. Primary button **"I take this"** → save.
4. **Error** — "Couldn't recognize / substance not found", retry + manual name entry.

### New/changed classes
- `mlkit/OcrScanner` — wraps ML Kit `TextRecognition`. Input `Uri`/`Bitmap` via
  `InputImage.fromFilePath`. Output: recognized text lines + a **candidate brand name**
  chosen by a pure heuristic (longest alphabetic lines, uppercase-weighted). The heuristic
  is a plain static method for unit testing without ML Kit on the JVM.
- `data/DrugInfoRepo` — OkHttp GET
  `https://api.fda.gov/drug/label.json?search=<query>&limit=1`. Parses
  `results[0].openfda.generic_name[]` (fallback `results[0].active_ingredient[]`). Returns
  `active ingredient` or a not-found result on the main thread. Timeout + failure handling.
  Parsing lives in a pure static method fed the raw JSON string (testable via fixtures).
- `UserMedsRepo.addScannedMed(uid, brand, activeSubstance, cb)` — writes
  `users/{uid}/myMeds/{id}` with `{ name, activeSubstance, source:"scan", addedAt }`.
  `id` = normalized active substance (fallback normalized brand). `ProfileActivity` shows
  scanned meds alongside catalog-saved ones (already reads name; add substance line).
- Cross-check: `ScanActivity` loads `CatalogRepo.all()` (or per selected country codes
  passed via intent extra) and runs `SearchFilter.filter(list, activeSubstance)`.

### Dependencies / manifest
- `gradle/libs.versions.toml`: add
  `mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version = "16.0.1" }`
  and wire `implementation libs.mlkit.text.recognition` in `app/build.gradle`.
- `AndroidManifest.xml`: declare `ScanActivity` (exported false); add a `FileProvider`
  (`res/xml/file_paths.xml`) for the capture Uri; add `<queries>`/intent for
  `ACTION_IMAGE_CAPTURE`. No `CAMERA` permission needed — `ActivityResultContracts.TakePicture`
  uses the system camera app. Gallery via `PickVisualMedia` (no storage permission).
- New strings added to `values/` and all locale folders (`values-en/es/be/uk/zh`).

## Data flow (scan)

```
[Take photo | Pick gallery] → Uri
   → OcrScanner (ML Kit, on-device) → lines + candidate brand
   → DrugInfoRepo (OpenFDA, OkHttp, background) → active ingredient
   → SearchFilter over CatalogRepo → restricted? (per selected countries)
   → Result card (user may edit brand) → "I take this"
   → AuthGate (login if needed) → UserMedsRepo.addScannedMed → Firestore
```

## Error handling
- No camera app / capture cancelled → back to Empty state, no crash.
- OCR yields no usable text → Error state with manual entry.
- OpenFDA timeout / non-200 / empty results → Error state; user can still save the brand
  with a blank/edited substance manually.
- Offline → OCR still works (on-device); OpenFDA step surfaces a network-error message.
- Not signed in at save time → existing redirect to `AuthActivity`, then return.

## Testing
- `OcrScanner` candidate-selection heuristic — unit tests over sample line lists.
- `DrugInfoRepo` JSON parsing — unit tests over OpenFDA fixture responses (hit + miss +
  malformed); network path via `MockWebServer` (already a dependency).
- Scanned-med id normalization — unit test.
- Existing test suite must stay green.

## Out of scope (YAGNI)
- RxNorm fallback (OpenFDA only for now; RxNorm noted as future).
- CameraX live-preview real-time OCR.
- Editing/merging duplicate scanned meds beyond id-normalization dedupe.
- Barcode/DataMatrix scanning.
```
