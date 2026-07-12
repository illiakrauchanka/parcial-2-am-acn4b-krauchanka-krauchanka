# Vyshyvanka Redesign + Medication Scan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebrand MedTraveler with a Belarusian "vyshyvanka" visual system (white/red, folk diamond ornament) and add a photo-scan feature: OCR a medicine box (Google ML Kit), resolve the active ingredient (OpenFDA), warn against the restriction catalog, save to the user's Firestore med list.

**Architecture:** Java Android app, Activity-per-screen with a shared `BaseActivity` (programmatic drawer/toolbar chrome). Redesign = new color/theme tokens + vector ornament drawables + layout refresh (no behavior change). Scan = new `ScanActivity` (4 UI states) with two pure-logic helpers: `OcrScanner` (ML Kit wrapper + static brand-candidate heuristic) and `DrugInfoRepo` (OkHttp → OpenFDA, static JSON parser). Save path extends `UserMedsRepo` with `addScannedMed`.

**Tech Stack:** Java 11, minSdk 24 / target 36, AndroidX + Material Components, Firebase Auth/Firestore, Room, OkHttp 4.12, Glide, ML Kit `text-recognition:16.0.1`, JUnit4 + MockWebServer.

## Global Constraints

- Java 11 source/target; app module namespace `com.davinci.medtraveler`.
- All user-visible strings live in `res/values/strings.xml` AND all 5 locale folders: `values-en`, `values-es`, `values-be`, `values-uk`, `values-zh` (default `values/` is Spanish).
- No new permissions except what the spec allows: NO `CAMERA` permission (system camera via `TakePicture` contract), no storage permissions (`PickVisualMedia`).
- Vector drawables only for ornament/icons — no raster assets.
- Status colors keep semantic names `status_allowed` / `status_restricted` / `status_penal`.
- Toolbar stays dark (`toolbar_background` `#191917`) with red ornament band beneath it; `windowLightStatusBar=false`.
- Network calls off the main thread; callbacks delivered to main thread (follow `CatalogUpdater` pattern: background `ExecutorService` + `runOnUiThread`/Handler).
- Run unit tests with `./gradlew :app:testDebugUnitTest`; existing suite must stay green.
- Commit after every task (conventional commits, e.g. `feat(scan): …`, `feat(theme): …`).

---

### Task 1: Vyshyvanka color tokens + theme + ornament drawables

**Files:**
- Modify: `app/src/main/res/values/colors.xml` (full rewrite of values)
- Modify: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/drawable/ornament_band.xml`
- Create: `app/src/main/res/drawable/ornament_watermark.xml`

**Interfaces:**
- Consumes: nothing (leaf task).
- Produces: color resources `@color/color_primary` (#C8102E), `@color/surface` (#FFFFFF), `@color/surface_card` (#FBF7F4), `@color/surface_variant` (#F2ECE8); drawables `@drawable/ornament_band` (tileable horizontal band, 24dp tall) and `@drawable/ornament_watermark` (240dp motif) used by Tasks 2–4 and 8.

- [ ] **Step 1: Rewrite `colors.xml` with the vyshyvanka palette**

Replace the whole file content with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Vyshyvanka palette: white ground, folk-red accent -->
    <color name="color_primary">#C8102E</color>
    <color name="color_primary_dark">#A50D26</color>
    <color name="color_accent">#C8102E</color>

    <color name="toolbar_background">#191917</color>

    <!-- Surfaces -->
    <color name="surface">#FFFFFF</color>
    <color name="surface_card">#FBF7F4</color>
    <color name="surface_variant">#F2ECE8</color>

    <!-- Text -->
    <color name="text_primary">#1A1A1A</color>
    <color name="text_secondary">#5C5C5C</color>
    <color name="text_on_primary">#FFFFFF</color>

    <!-- Status semantic colors -->
    <color name="status_allowed">#2E7D46</color>
    <color name="status_restricted">#C77800</color>
    <color name="status_penal">#8E1420</color>

    <!-- Quote styling -->
    <color name="quote_background">#FBF2F0</color>
    <color name="quote_border">#C8102E</color>

    <!-- Highlight for the user's saved meds -->
    <color name="mine_highlight">#FDECEC</color>

    <!-- Ornament -->
    <color name="ornament_red">#C8102E</color>
    <color name="ornament_faint">#F5D9DD</color>
</resources>
```

Note: `surface_variant` keeps its name because `row_country_section_header.xml` / other layouts may reference it — grep before assuming: `grep -rn "surface_variant\|quote_background" app/src/main/res/layout/`. Keep every existing color name present (same names, new values) so no layout breaks.

- [ ] **Step 2: Create the tileable ornament band**

`app/src/main/res/drawable/ornament_band.xml` — a `VectorDrawable` of repeating diamonds (rhombs) in folk style. Width 96dp holding 4 diamond motifs; consumers stretch/tile it. Content:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp"
    android:height="12dp"
    android:viewportWidth="96"
    android:viewportHeight="12">
    <!-- ground -->
    <path android:fillColor="@color/color_primary" android:pathData="M0,0h96v12h-96z"/>
    <!-- white diamonds: 4 large motifs, centers at x=12,36,60,84 -->
    <path android:fillColor="@color/text_on_primary"
        android:pathData="M12,2 L16,6 L12,10 L8,6 Z"/>
    <path android:fillColor="@color/text_on_primary"
        android:pathData="M36,2 L40,6 L36,10 L32,6 Z"/>
    <path android:fillColor="@color/text_on_primary"
        android:pathData="M60,2 L64,6 L60,10 L56,6 Z"/>
    <path android:fillColor="@color/text_on_primary"
        android:pathData="M84,2 L88,6 L84,10 L80,6 Z"/>
    <!-- small red inner diamonds -->
    <path android:fillColor="@color/color_primary"
        android:pathData="M12,4.5 L13.5,6 L12,7.5 L10.5,6 Z"/>
    <path android:fillColor="@color/color_primary"
        android:pathData="M36,4.5 L37.5,6 L36,7.5 L34.5,6 Z"/>
    <path android:fillColor="@color/color_primary"
        android:pathData="M60,4.5 L61.5,6 L60,7.5 L58.5,6 Z"/>
    <path android:fillColor="@color/color_primary"
        android:pathData="M84,4.5 L85.5,6 L84,7.5 L82.5,6 Z"/>
    <!-- connecting small white diamonds between motifs, centers x=0,24,48,72,96 -->
    <path android:fillColor="@color/text_on_primary"
        android:pathData="M24,4.5 L25.5,6 L24,7.5 L22.5,6 Z"/>
    <path android:fillColor="@color/text_on_primary"
        android:pathData="M48,4.5 L49.5,6 L48,7.5 L46.5,6 Z"/>
    <path android:fillColor="@color/text_on_primary"
        android:pathData="M72,4.5 L73.5,6 L72,7.5 L70.5,6 Z"/>
    <path android:fillColor="@color/text_on_primary"
        android:pathData="M0,4.5 L1.5,6 L0,7.5 L-1.5,6 Z"/>
    <path android:fillColor="@color/text_on_primary"
        android:pathData="M96,4.5 L97.5,6 L96,7.5 L94.5,6 Z"/>
</vector>
```

- [ ] **Step 3: Create the watermark motif**

`app/src/main/res/drawable/ornament_watermark.xml` — one large faint diamond motif for the Welcome screen background:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="240dp"
    android:height="240dp"
    android:viewportWidth="240"
    android:viewportHeight="240">
    <path android:fillColor="@color/ornament_faint"
        android:pathData="M120,10 L230,120 L120,230 L10,120 Z"/>
    <path android:fillColor="@color/surface"
        android:pathData="M120,40 L200,120 L120,200 L40,120 Z"/>
    <path android:fillColor="@color/ornament_faint"
        android:pathData="M120,70 L170,120 L120,170 L70,120 Z"/>
    <path android:fillColor="@color/surface"
        android:pathData="M120,95 L145,120 L120,145 L95,120 Z"/>
    <path android:fillColor="@color/ornament_faint"
        android:pathData="M120,108 L132,120 L120,132 L108,120 Z"/>
</vector>
```

- [ ] **Step 4: Update `themes.xml`**

Replace file content with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.MedTraveler" parent="Theme.MaterialComponents.Light.NoActionBar">
        <item name="colorPrimary">@color/color_primary</item>
        <item name="colorPrimaryDark">@color/color_primary_dark</item>
        <item name="colorOnPrimary">@color/text_on_primary</item>
        <item name="colorSecondary">@color/color_accent</item>
        <item name="colorAccent">@color/color_primary</item>
        <item name="android:colorBackground">@color/surface</item>
        <item name="android:statusBarColor">@color/toolbar_background</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:textColorPrimary">@color/text_primary</item>
        <item name="android:textColorSecondary">@color/text_secondary</item>
        <item name="materialCardViewStyle">@style/Widget.MedTraveler.Card</item>
    </style>

    <style name="Widget.MedTraveler.Card" parent="Widget.MaterialComponents.CardView">
        <item name="cardBackgroundColor">@color/surface_card</item>
        <item name="cardCornerRadius">12dp</item>
        <item name="cardElevation">1dp</item>
        <item name="strokeColor">@color/surface_variant</item>
        <item name="strokeWidth">1dp</item>
    </style>

    <style name="SectionHeader" parent="android:Widget.TextView">
        <item name="android:layout_width">match_parent</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:textSize">@dimen/text_size_section</item>
        <item name="android:textStyle">bold</item>
        <item name="android:textColor">@color/color_primary</item>
        <item name="android:layout_marginBottom">@dimen/spacing_small</item>
    </style>
</resources>
```

- [ ] **Step 5: Build to verify resources compile**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL (vector paths and theme attrs resolve).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values/themes.xml app/src/main/res/drawable/ornament_band.xml app/src/main/res/drawable/ornament_watermark.xml
git commit -m "feat(theme): vyshyvanka palette, Material card style, folk ornament vectors"
```

---

### Task 2: Ornament band in BaseActivity chrome + nav header redesign

**Files:**
- Modify: `app/src/main/java/com/davinci/medtraveler/ui/BaseActivity.java` (in `setContentWithChrome`, after the toolbar is added)
- Modify: `app/src/main/res/layout/nav_header.xml`

**Interfaces:**
- Consumes: `@drawable/ornament_band` from Task 1.
- Produces: every screen using `setContentWithChrome` automatically shows the red ornament band under the dark toolbar; nav drawer header shows ornament band + app name + DB status (existing id `txt_db_status` must remain).

- [ ] **Step 1: Add the ornament band under the toolbar in `BaseActivity`**

In `setContentWithChrome`, right after `col.addView(toolbar, …)` (currently line ~46–47), insert:

```java
        // Vyshyvanka ornament band — brand element under the dark toolbar on every screen.
        android.widget.ImageView band = new android.widget.ImageView(this);
        band.setImageResource(R.drawable.ornament_band);
        band.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        band.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        col.addView(band, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.ornament_band_height)));
```

And add to `app/src/main/res/values/dimens.xml`:

```xml
    <dimen name="ornament_band_height">12dp</dimen>
```

- [ ] **Step 2: Redesign `nav_header.xml`**

Read the current file first, keep `@+id/txt_db_status` (BaseActivity binds it). Replace content with a header that shows the ornament band on top, bold app name, and the DB status line:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@color/surface_card">

    <ImageView
        android:layout_width="match_parent"
        android:layout_height="@dimen/ornament_band_height"
        android:src="@drawable/ornament_band"
        android:scaleType="fitXY"
        android:importantForAccessibility="no"/>

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_marginTop="20dp"
        android:text="@string/app_name"
        android:textColor="@color/color_primary"
        android:textSize="22sp"
        android:textStyle="bold"/>

    <TextView
        android:id="@+id/txt_db_status"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_marginTop="4dp"
        android:layout_marginBottom="16dp"
        android:textColor="@color/text_secondary"
        android:textSize="13sp"/>
</LinearLayout>
```

If the current `nav_header.xml` contains additional bound ids, keep them — check with `grep -n "findViewById" app/src/main/java/com/davinci/medtraveler/ui/BaseActivity.java`.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/davinci/medtraveler/ui/BaseActivity.java app/src/main/res/layout/nav_header.xml app/src/main/res/values/dimens.xml
git commit -m "feat(theme): ornament band in chrome + redesigned nav drawer header"
```

---

### Task 3: Redesign Welcome + List + Detail layouts

**Files:**
- Modify: `app/src/main/res/layout/activity_welcome.xml`
- Modify: `app/src/main/res/layout/activity_medicine_list.xml`
- Modify: `app/src/main/res/layout/activity_medicine_detail.xml`
- Modify: `app/src/main/res/layout/row_medicine.xml`
- Modify: `app/src/main/res/layout/row_country_section_header.xml`

**Interfaces:**
- Consumes: Task 1 tokens (`surface_card`, `Widget.MedTraveler.Card` via theme default, `ornament_watermark`).
- Produces: same view IDs as before — **do not rename or remove any `@+id/`** (activities bind them; verify each id referenced in the corresponding Activity still exists after edits).

- [ ] **Step 1: Inventory bound IDs per layout (safety net)**

Run and save output:
```bash
grep -n "R.id\." app/src/main/java/com/davinci/medtraveler/ui/WelcomeActivity.java app/src/main/java/com/davinci/medtraveler/ui/MedicineListActivity.java app/src/main/java/com/davinci/medtraveler/ui/MedicineDetailActivity.java
```
Every id in that list must survive the redesign. Known critical ones: `sections_container`, `row_root`, `txt_medicine_name`, `txt_medicine_subtitle`, `txt_status_badge`, `img_medicine`, `txt_mine_badge`, `img_flag`, `txt_country`, `txt_meta`, `btn_back`, `btn_open_source`, `btn_add_trip`.

- [ ] **Step 2: Welcome — add watermark + card panels**

Read `activity_welcome.xml` first. Apply, preserving all ids and the existing view hierarchy semantics:
- Root gets `android:background="@color/surface"`.
- Add as FIRST child of the root (behind content) a watermark:
```xml
    <ImageView
        android:layout_width="240dp"
        android:layout_height="240dp"
        android:layout_gravity="bottom|end"
        android:src="@drawable/ornament_watermark"
        android:importantForAccessibility="no"/>
```
(If the root is `ConstraintLayout`, constrain it `bottom/end` to parent instead of `layout_gravity`; if the root is a `ScrollView`, wrap its single child in a `FrameLayout` and put the watermark first inside that frame.)
- Wrap the EULA block and the country-picker block each in a `com.google.android.material.card.MaterialCardView` (theme style applies automatically) with `android:layout_margin="12dp"` and inner padding `16dp`, keeping inner views/ids untouched.
- The primary button (`CHECK my meds`): ensure `android:backgroundTint="@color/color_primary"` and `android:textColor="@color/text_on_primary"`.

- [ ] **Step 3: Medicine list rows → cards**

In `row_medicine.xml`: wrap existing content in `MaterialCardView` **only if** the current root is not already a card; simpler and safer — keep the current root (which carries `@+id/row_root`) and set on it `android:background="@drawable/bg_card_row"`; create `app/src/main/res/drawable/bg_card_row.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="@color/surface_card"/>
    <corners android:radius="12dp"/>
    <stroke android:width="1dp" android:color="@color/surface_variant"/>
</shape>
```

Also add `android:layout_marginBottom="8dp"` and horizontal margins `12dp` to the row root, `android:padding="12dp"` inside. **Caveat:** `MedicineListActivity.decorateMine()` swaps the row background between `bg_mine_highlight` and `android.R.color.transparent`; update that call to swap between `R.drawable.bg_mine_highlight` and `R.drawable.bg_card_row` instead, and update `bg_mine_highlight.xml` to a rounded shape with `@color/mine_highlight` solid + `@color/color_primary` 1dp stroke and 12dp radius so "mine" rows read as red-accented cards.

In `row_country_section_header.xml`: add the flag + country name row a red underline accent — insert after the existing content a 2dp view:
```xml
    <View
        android:layout_width="48dp"
        android:layout_height="2dp"
        android:layout_marginTop="4dp"
        android:background="@color/color_primary"/>
```

- [ ] **Step 4: Detail — card sections**

Read `activity_medicine_detail.xml`. Wrap each logical section (header block, description, key facts container, law excerpt, penalty) in `MaterialCardView` with `12dp` margins/`16dp` padding, preserving all ids. Section title TextViews get `style="@style/SectionHeader"` if not already. Keep the quote/law block using `bg_quote` (already red-bordered after Task 1 recolor).

- [ ] **Step 5: Build + install smoke test**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL.
Then verify ids: re-run the Step 1 grep — every `R.id.X` must appear in the layouts: `for id in sections_container row_root txt_medicine_name txt_status_badge img_medicine txt_mine_badge btn_add_trip; do grep -rln "@+id/$id" app/src/main/res/layout/ || echo "MISSING: $id"; done`
Expected: no `MISSING:` lines.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/ app/src/main/res/drawable/bg_card_row.xml app/src/main/res/drawable/bg_mine_highlight.xml app/src/main/java/com/davinci/medtraveler/ui/MedicineListActivity.java
git commit -m "feat(theme): redesign welcome/list/detail with vyshyvanka cards and accents"
```

---

### Task 4: Redesign Search + Auth + Profile layouts

**Files:**
- Modify: `app/src/main/res/layout/activity_search.xml`
- Modify: `app/src/main/res/layout/activity_auth.xml`
- Modify: `app/src/main/res/layout/activity_profile.xml`
- Modify: `app/src/main/res/layout/row_search.xml`
- Modify: `app/src/main/res/layout/row_my_med.xml`

**Interfaces:**
- Consumes: Task 1 tokens + `bg_card_row` from Task 3.
- Produces: same view IDs preserved (verify against `SearchActivity`, `AuthActivity`, `ProfileActivity` binds — includes `my_meds_container`, `txt_empty`, `btn_logout`, `btn_delete_account`, `txt_name`, `btn_remove`).

- [ ] **Step 1: Inventory bound IDs**

```bash
grep -n "R.id\." app/src/main/java/com/davinci/medtraveler/ui/SearchActivity.java app/src/main/java/com/davinci/medtraveler/ui/AuthActivity.java app/src/main/java/com/davinci/medtraveler/ui/ProfileActivity.java
```
All listed ids must survive.

- [ ] **Step 2: Search**

`activity_search.xml`: search `EditText` gets a rounded outline — create `app/src/main/res/drawable/bg_search_field.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="@color/surface_card"/>
    <corners android:radius="24dp"/>
    <stroke android:width="1dp" android:color="@color/color_primary"/>
</shape>
```

Set it as `android:background` on the search input, `android:padding="14dp"`. `row_search.xml` rows get `android:background="@drawable/bg_card_row"` + margins as in Task 3 Step 3.

- [ ] **Step 3: Auth**

`activity_auth.xml`: inputs get `@drawable/bg_search_field` background; primary submit buttons get `android:backgroundTint="@color/color_primary"`; the Google button keeps its branding untouched. Wrap the form in a `MaterialCardView` (12dp margin, 16dp padding) if the root allows; ids untouched.

- [ ] **Step 4: Profile**

`activity_profile.xml`: `row_my_med.xml` rows get `bg_card_row` background + margins; `btn_remove` gets `android:textColor="@color/color_primary"`; `btn_delete_account` gets `android:textColor="@color/status_penal"` (destructive action); `btn_logout` stays primary-tinted.

- [ ] **Step 5: Build + id check**

Run: `./gradlew :app:assembleDebug -q` → BUILD SUCCESSFUL.
Re-run Step 1 grep and confirm each id exists in layouts.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/ app/src/main/res/drawable/bg_search_field.xml
git commit -m "feat(theme): redesign search/auth/profile screens"
```

---

### Task 5: ML Kit dependency + OcrScanner with tested brand-candidate heuristic

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle`
- Create: `app/src/main/java/com/davinci/medtraveler/mlkit/OcrScanner.java`
- Test: `app/src/test/java/com/davinci/medtraveler/mlkit/OcrScannerHeuristicTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `OcrScanner.pickBrandCandidate(List<String> lines) : String` — static, pure; returns best brand-name guess or `null`.
  - `OcrScanner.recognize(Context ctx, Uri imageUri, ResultCallback cb)` — instance method; `interface ResultCallback { void onResult(List<String> lines, String candidate); void onError(Exception e); }`; callbacks on main thread.

- [ ] **Step 1: Add ML Kit to the version catalog**

In `gradle/libs.versions.toml` `[versions]` add:
```toml
mlkitTextRecognition = "16.0.1"
```
In `[libraries]` add:
```toml
mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkitTextRecognition" }
```

In `app/build.gradle` `dependencies` add after `implementation libs.okhttp`:
```groovy
    implementation libs.mlkit.text.recognition
```

Run: `./gradlew :app:assembleDebug -q` → BUILD SUCCESSFUL (dependency resolves).

- [ ] **Step 2: Write the failing heuristic test**

`app/src/test/java/com/davinci/medtraveler/mlkit/OcrScannerHeuristicTest.java`:

```java
package com.davinci.medtraveler.mlkit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class OcrScannerHeuristicTest {

    @Test public void picksLongestAlphabeticLine() {
        String got = OcrScanner.pickBrandCandidate(Arrays.asList(
                "20 tablets", "IBUPROFEN", "200 mg", "exp 2027"));
        assertEquals("IBUPROFEN", got);
    }

    @Test public void uppercaseBeatsLongerMixedCase() {
        // Brand names on boxes are usually ALL-CAPS; prefer them over longer prose.
        String got = OcrScanner.pickBrandCandidate(Arrays.asList(
                "NUROFEN", "pain relief for adults and children"));
        assertEquals("NUROFEN", got);
    }

    @Test public void skipsDosageAndNumericLines() {
        String got = OcrScanner.pickBrandCandidate(Arrays.asList(
                "500 mg", "10 x 10", "PARACETAMOL"));
        assertEquals("PARACETAMOL", got);
    }

    @Test public void cyrillicBrandIsAccepted() {
        String got = OcrScanner.pickBrandCandidate(Arrays.asList(
                "АНАЛЬГИН", "10 таблеток"));
        assertEquals("АНАЛЬГИН", got);
    }

    @Test public void trimsAndIgnoresBlankLines() {
        String got = OcrScanner.pickBrandCandidate(Arrays.asList(
                "  ", "", "  ASPIRIN  "));
        assertEquals("ASPIRIN", got);
    }

    @Test public void emptyInputGivesNull() {
        assertNull(OcrScanner.pickBrandCandidate(Collections.emptyList()));
        assertNull(OcrScanner.pickBrandCandidate(null));
    }

    @Test public void allNumericInputGivesNull() {
        assertNull(OcrScanner.pickBrandCandidate(Arrays.asList("200 mg", "12+")));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.davinci.medtraveler.mlkit.OcrScannerHeuristicTest" 2>&1 | tail -20`
Expected: FAIL — class `OcrScanner` does not exist (compilation error).

- [ ] **Step 4: Implement `OcrScanner`**

`app/src/main/java/com/davinci/medtraveler/mlkit/OcrScanner.java`:

```java
package com.davinci.medtraveler.mlkit;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Wraps ML Kit on-device text recognition and derives a "brand name" candidate from
 *  the raw OCR lines. The candidate heuristic is a pure static function so it can be
 *  unit-tested on the JVM without ML Kit. */
public class OcrScanner {

    public interface ResultCallback {
        void onResult(List<String> lines, String candidate);
        void onError(Exception e);
    }

    private final Handler main = new Handler(Looper.getMainLooper());

    /** Runs on-device OCR over the given image. Callbacks fire on the main thread. */
    public void recognize(Context ctx, Uri imageUri, ResultCallback cb) {
        InputImage image;
        try {
            image = InputImage.fromFilePath(ctx, imageUri);
        } catch (Exception e) {
            main.post(() -> cb.onError(e));
            return;
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener(text -> {
                    List<String> lines = flatten(text);
                    String candidate = pickBrandCandidate(lines);
                    main.post(() -> cb.onResult(lines, candidate));
                })
                .addOnFailureListener(e -> main.post(() -> cb.onError(e)));
    }

    private static List<String> flatten(Text text) {
        List<String> out = new ArrayList<>();
        for (Text.TextBlock b : text.getTextBlocks())
            for (Text.Line l : b.getLines()) out.add(l.getText());
        return out;
    }

    /** Best-guess brand name from OCR lines. Boxes print the brand big and usually in
     *  ALL CAPS, so we score: letters only (dosage lines like "200 mg" are skipped),
     *  +length, x2 when the line is fully upper-case. Latin and Cyrillic both count.
     *  Returns null when no line qualifies. */
    public static String pickBrandCandidate(List<String> lines) {
        if (lines == null) return null;
        String best = null;
        int bestScore = 0;
        for (String raw : lines) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isEmpty()) continue;
            int letters = 0, digits = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isLetter(c)) letters++;
                else if (Character.isDigit(c)) digits++;
            }
            if (letters < 3 || digits > 0) continue;      // dosage / count / expiry lines
            int score = letters;
            if (s.equals(s.toUpperCase(Locale.ROOT)) && !s.equals(s.toLowerCase(Locale.ROOT)))
                score *= 2;                                // ALL-CAPS brand bonus
            if (score > bestScore) { bestScore = score; best = s; }
        }
        return best;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.davinci.medtraveler.mlkit.OcrScannerHeuristicTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, 7 tests pass.

Check `uppercaseBeatsLongerMixedCase`: "NUROFEN" = 7 letters ×2 = 14; "pain relief for adults and children" = 31 letters, no caps bonus = 31. **31 > 14 — the test as written FAILS with this implementation.** Fix the heuristic, not the test: lines longer than ~20 chars are prose, not brand names. Add after the `letters < 3` guard:

```java
            if (s.length() > 20) continue;                // prose line, not a brand
```

Re-run: all 7 pass.

- [ ] **Step 6: Full test suite**

Run: `./gradlew :app:testDebugUnitTest 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL — existing tests still green.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle app/src/main/java/com/davinci/medtraveler/mlkit/ app/src/test/java/com/davinci/medtraveler/mlkit/
git commit -m "feat(scan): ML Kit dependency + OcrScanner with tested brand heuristic"
```

---

### Task 6: DrugInfoRepo — OpenFDA lookup with tested parser

**Files:**
- Create: `app/src/main/java/com/davinci/medtraveler/data/DrugInfoRepo.java`
- Test: `app/src/test/java/com/davinci/medtraveler/data/DrugInfoRepoParseTest.java`

**Interfaces:**
- Consumes: OkHttp (already a dependency), `org.json` (test scope already; runtime uses Android's built-in `org.json`).
- Produces:
  - `DrugInfoRepo.parseActiveIngredient(String json) : String` — static, pure; returns lowercase active ingredient or `null`.
  - `DrugInfoRepo.lookup(String brandName, Callback cb)` — instance; `interface Callback { void onFound(String activeIngredient); void onNotFound(); void onError(Exception e); }`; callbacks on main thread; background thread via single-thread executor; 10s timeouts.
  - `DrugInfoRepo.shutdown()` — stops the executor (call from `onDestroy`).

- [ ] **Step 1: Write the failing parser test**

`app/src/test/java/com/davinci/medtraveler/data/DrugInfoRepoParseTest.java`:

```java
package com.davinci.medtraveler.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DrugInfoRepoParseTest {

    @Test public void parsesGenericName() {
        String json = "{\"results\":[{\"openfda\":{\"generic_name\":[\"IBUPROFEN\"],"
                + "\"brand_name\":[\"ADVIL\"]}}]}";
        assertEquals("ibuprofen", DrugInfoRepo.parseActiveIngredient(json));
    }

    @Test public void fallsBackToActiveIngredientField() {
        String json = "{\"results\":[{\"active_ingredient\":"
                + "[\"Active ingredient: Diphenhydramine HCl 25 mg\"],\"openfda\":{}}]}";
        assertEquals("active ingredient: diphenhydramine hcl 25 mg",
                DrugInfoRepo.parseActiveIngredient(json));
    }

    @Test public void emptyResultsGivesNull() {
        assertNull(DrugInfoRepo.parseActiveIngredient("{\"results\":[]}"));
    }

    @Test public void errorPayloadGivesNull() {
        assertNull(DrugInfoRepo.parseActiveIngredient(
                "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"No matches found!\"}}"));
    }

    @Test public void malformedJsonGivesNull() {
        assertNull(DrugInfoRepo.parseActiveIngredient("not json at all"));
        assertNull(DrugInfoRepo.parseActiveIngredient(null));
        assertNull(DrugInfoRepo.parseActiveIngredient(""));
    }

    @Test public void missingOpenfdaBlockAndNoActiveIngredientGivesNull() {
        assertNull(DrugInfoRepo.parseActiveIngredient("{\"results\":[{\"id\":\"x\"}]}"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.davinci.medtraveler.data.DrugInfoRepoParseTest" 2>&1 | tail -20`
Expected: FAIL — `DrugInfoRepo` not found (compilation error).

- [ ] **Step 3: Implement `DrugInfoRepo`**

`app/src/main/java/com/davinci/medtraveler/data/DrugInfoRepo.java`:

```java
package com.davinci.medtraveler.data;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Resolves a medicine brand name to its active ingredient via the public OpenFDA
 *  Drug Label API (https://api.fda.gov/drug/label.json — no API key for low volume).
 *  Network work runs on a private executor; callbacks fire on the main thread, matching
 *  the CatalogUpdater pattern. JSON parsing is a pure static method for JVM tests. */
public class DrugInfoRepo {

    public interface Callback {
        void onFound(String activeIngredient);
        void onNotFound();
        void onError(Exception e);
    }

    /** Visible for tests: production code always uses the real OpenFDA base. */
    static String baseUrl = "https://api.fda.gov/drug/label.json";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public void lookup(String brandName, Callback cb) {
        if (brandName == null || brandName.trim().isEmpty()) {
            main.post(cb::onNotFound);
            return;
        }
        String q = brandName.trim();
        io.execute(() -> {
            try {
                HttpUrl url = HttpUrl.parse(baseUrl).newBuilder()
                        .addQueryParameter("search",
                                "openfda.brand_name:\"" + q + "\"")
                        .addQueryParameter("limit", "1")
                        .build();
                try (Response r = client.newCall(
                        new Request.Builder().url(url).build()).execute()) {
                    // OpenFDA answers 404 with an error JSON body when nothing matches —
                    // that is "not found", not a transport failure.
                    String body = r.body() == null ? null : r.body().string();
                    String ingredient = parseActiveIngredient(body);
                    if (ingredient != null) main.post(() -> cb.onFound(ingredient));
                    else main.post(cb::onNotFound);
                }
            } catch (Exception e) {
                main.post(() -> cb.onError(e));
            }
        });
    }

    public void shutdown() { io.shutdown(); }

    /** Extracts the active ingredient from an OpenFDA drug/label response.
     *  Prefers openfda.generic_name[0]; falls back to active_ingredient[0].
     *  Returns a lowercase string, or null when absent/malformed. */
    public static String parseActiveIngredient(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray results = root.optJSONArray("results");
            if (results == null || results.length() == 0) return null;
            JSONObject first = results.getJSONObject(0);
            JSONObject openfda = first.optJSONObject("openfda");
            if (openfda != null) {
                JSONArray generic = openfda.optJSONArray("generic_name");
                if (generic != null && generic.length() > 0) {
                    String s = generic.optString(0, "").trim();
                    if (!s.isEmpty()) return s.toLowerCase(Locale.ROOT);
                }
            }
            JSONArray active = first.optJSONArray("active_ingredient");
            if (active != null && active.length() > 0) {
                String s = active.optString(0, "").trim();
                if (!s.isEmpty()) return s.toLowerCase(Locale.ROOT);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Run parser tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.davinci.medtraveler.data.DrugInfoRepoParseTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, 6 tests pass. (Unit tests use `testImplementation libs.org.json`, already present, so `JSONObject` works on the JVM.)

- [ ] **Step 5: Full suite**

Run: `./gradlew :app:testDebugUnitTest 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/davinci/medtraveler/data/DrugInfoRepo.java app/src/test/java/com/davinci/medtraveler/data/DrugInfoRepoParseTest.java
git commit -m "feat(scan): DrugInfoRepo resolves active ingredient via OpenFDA"
```

---

### Task 7: UserMedsRepo.addScannedMed + id normalization (tested)

**Files:**
- Modify: `app/src/main/java/com/davinci/medtraveler/data/UserMedsRepo.java`
- Test: `app/src/test/java/com/davinci/medtraveler/data/ScannedMedIdTest.java`

**Interfaces:**
- Consumes: existing `UserMedsRepo` (Firestore `users/{uid}/myMeds/{id}`), `DoneCallback`.
- Produces:
  - `UserMedsRepo.scannedMedId(String brand, String activeSubstance) : String` — static, pure; normalized doc id, prefers substance over brand; `null` when both blank.
  - `UserMedsRepo.addScannedMed(String uid, String brand, String activeSubstance, DoneCallback cb)` — writes `{ name: brand, activeSubstance, source: "scan", addedAt }`; `countryCode` intentionally absent (scan is country-agnostic).
  - `MyMed` gains a nullable `activeSubstance` field read in `loadMyMeds` (used by Task 9).

- [ ] **Step 1: Write the failing id-normalization test**

`app/src/test/java/com/davinci/medtraveler/data/ScannedMedIdTest.java`:

```java
package com.davinci.medtraveler.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ScannedMedIdTest {

    @Test public void prefersSubstanceOverBrand() {
        assertEquals("scan_ibuprofen", UserMedsRepo.scannedMedId("Nurofen", "Ibuprofen"));
    }

    @Test public void fallsBackToBrandWhenNoSubstance() {
        assertEquals("scan_nurofen", UserMedsRepo.scannedMedId("Nurofen", null));
        assertEquals("scan_nurofen", UserMedsRepo.scannedMedId("Nurofen", "  "));
    }

    @Test public void normalizesSpacesAndCase() {
        assertEquals("scan_acetylsalicylic_acid",
                UserMedsRepo.scannedMedId(null, "  Acetylsalicylic ACID "));
    }

    @Test public void stripsFirestoreUnsafeChars() {
        // '/' is illegal in a Firestore document id.
        assertEquals("scan_a_b", UserMedsRepo.scannedMedId(null, "a/b"));
    }

    @Test public void nullWhenBothBlank() {
        assertNull(UserMedsRepo.scannedMedId(null, null));
        assertNull(UserMedsRepo.scannedMedId(" ", ""));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.davinci.medtraveler.data.ScannedMedIdTest" 2>&1 | tail -20`
Expected: FAIL — no method `scannedMedId` (compilation error).

- [ ] **Step 3: Implement in `UserMedsRepo`**

Add to `UserMedsRepo.java` (below `MyMed`, above the field declarations). Also extend `MyMed`:

```java
    public static final class MyMed {
        public final String medId, name, countryCode;
        /** Active substance for scan-sourced meds; null for catalog-sourced ones. */
        public final String activeSubstance;
        public MyMed(String medId, String name, String countryCode, String activeSubstance) {
            this.medId = medId; this.name = name; this.countryCode = countryCode;
            this.activeSubstance = activeSubstance;
        }
    }
```

Update the constructor call in `loadMyMeds` to:

```java
                        out.add(new MyMed(d.getId(), d.getString("name"),
                                d.getString("countryCode"), d.getString("activeSubstance")));
```

Add the two new members:

```java
    /** Normalized Firestore doc id for a scanned med: "scan_" + lowercased key with
     *  every non-alphanumeric run collapsed to '_'. Prefers the active substance (stable
     *  across brands); falls back to the brand. Null when both inputs are blank. */
    public static String scannedMedId(String brand, String activeSubstance) {
        String key = activeSubstance != null && !activeSubstance.trim().isEmpty()
                ? activeSubstance : brand;
        if (key == null || key.trim().isEmpty()) return null;
        String norm = key.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}]+", "_")
                .replaceAll("^_+|_+$", "");
        return norm.isEmpty() ? null : "scan_" + norm;
    }

    /** Saves a medicine captured via photo scan under users/{uid}/myMeds/{scannedMedId}.
     *  No countryCode: a scanned med is the user's own, not tied to a catalog country. */
    public void addScannedMed(String uid, String brand, String activeSubstance, DoneCallback cb) {
        String id = scannedMedId(brand, activeSubstance);
        if (id == null) { cb.onDone(false); return; }
        Map<String, Object> data = new HashMap<>();
        data.put("name", brand);
        data.put("activeSubstance", activeSubstance);
        data.put("source", "scan");
        data.put("addedAt", System.currentTimeMillis());
        db.collection("users").document(uid).collection("myMeds").document(id)
                .set(data)
                .addOnSuccessListener(x -> cb.onDone(true))
                .addOnFailureListener(e -> cb.onDone(false));
    }
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.davinci.medtraveler.data.ScannedMedIdTest" 2>&1 | tail -5`
Expected: 5 tests pass.

Note: unit tests only touch the static method; the class references Firebase but JVM tests never instantiate it, so no Firebase-on-JVM problem (same as existing tests).

- [ ] **Step 5: Fix the other `MyMed` constructor callers**

Run: `grep -rn "new MyMed(" app/src/`
Any caller besides `loadMyMeds` (e.g. tests) must pass the 4th arg (`null`). Fix compile errors: `./gradlew :app:assembleDebug -q` → BUILD SUCCESSFUL.

- [ ] **Step 6: Full suite + commit**

Run: `./gradlew :app:testDebugUnitTest 2>&1 | tail -5` → BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/davinci/medtraveler/data/UserMedsRepo.java app/src/test/java/com/davinci/medtraveler/data/ScannedMedIdTest.java
git commit -m "feat(scan): addScannedMed + normalized scanned-med ids in UserMedsRepo"
```

---

### Task 8: ScanActivity — layout, states, wiring (camera/gallery → OCR → OpenFDA → check → save)

**Files:**
- Create: `app/src/main/res/layout/activity_scan.xml`
- Create: `app/src/main/res/xml/file_paths.xml`
- Create: `app/src/main/java/com/davinci/medtraveler/ui/ScanActivity.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml` (Spanish default — new strings; locale folders in Task 10)

**Interfaces:**
- Consumes: `OcrScanner.recognize` / `pickBrandCandidate` (Task 5), `DrugInfoRepo.lookup/shutdown` (Task 6), `UserMedsRepo.addScannedMed` (Task 7), `CatalogRepo.all()`, `SearchFilter.filter`, `AuthManager.currentUid`, `BaseActivity.setContentWithChrome/showProgress`, `MedicineListActivity.EXTRA_COUNTRY_CODES` / `EXTRA_MED_ID`.
- Produces: `ScanActivity` started with optional `EXTRA_COUNTRY_CODES` (String[]); drawer/toolbar entry points added in Task 9.

- [ ] **Step 1: Manifest — activity + FileProvider + camera queries**

In `AndroidManifest.xml`:

Inside `<queries>` add (next to the mailto intent):
```xml
        <intent>
            <action android:name="android.media.action.IMAGE_CAPTURE" />
        </intent>
```

Inside `<application>` add:
```xml
        <activity android:name=".ui.ScanActivity" android:exported="false" />

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

Create `app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="scans" path="scans/" />
</paths>
```

- [ ] **Step 2: Strings (Spanish default)**

Append to `app/src/main/res/values/strings.xml`:

```xml
    <!-- Scan screen -->
    <string name="menu_scan">Escanear medicamento</string>
    <string name="scan_title">Escanear medicamento</string>
    <string name="scan_hint">Sacá una foto del envase para detectar la sustancia activa.</string>
    <string name="scan_take_photo">Sacar foto</string>
    <string name="scan_pick_gallery">Elegir de la galería</string>
    <string name="scan_recognizing">Reconociendo texto…</string>
    <string name="scan_looking_up">Buscando sustancia activa…</string>
    <string name="scan_brand_label">Nombre detectado</string>
    <string name="scan_substance_label">Sustancia activa</string>
    <string name="scan_substance_unknown">No identificada — podés editar el nombre y reintentar</string>
    <string name="scan_warning_restricted">¡Atención! Esta sustancia figura en el listado de tus países de destino. Tocá para ver el detalle.</string>
    <string name="scan_save">Tomo este medicamento</string>
    <string name="scan_retry">Reintentar</string>
    <string name="scan_error_ocr">No se pudo reconocer texto en la imagen</string>
    <string name="scan_error_network">Error de red al consultar la sustancia</string>
    <string name="scan_saved">Guardado en mis medicamentos</string>
    <string name="scan_save_fail">No se pudo guardar</string>
    <string name="cd_scan_preview">Foto del medicamento</string>
```

- [ ] **Step 3: Layout `activity_scan.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/surface">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- STATE: empty -->
        <LinearLayout
            android:id="@+id/scan_state_empty"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center_horizontal">

            <ImageView
                android:layout_width="160dp"
                android:layout_height="160dp"
                android:layout_marginTop="24dp"
                android:src="@drawable/ornament_watermark"
                android:importantForAccessibility="no"/>

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:gravity="center"
                android:text="@string/scan_hint"
                android:textColor="@color/text_secondary"
                android:textSize="15sp"/>

            <Button
                android:id="@+id/btn_take_photo"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="24dp"
                android:backgroundTint="@color/color_primary"
                android:text="@string/scan_take_photo"
                android:textColor="@color/text_on_primary"/>

            <Button
                android:id="@+id/btn_pick_gallery"
                style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="@string/scan_pick_gallery"
                android:textColor="@color/color_primary"/>
        </LinearLayout>

        <!-- Shared photo preview (recognizing + result states) -->
        <ImageView
            android:id="@+id/img_scan_preview"
            android:layout_width="match_parent"
            android:layout_height="220dp"
            android:layout_marginTop="8dp"
            android:scaleType="centerCrop"
            android:visibility="gone"
            android:contentDescription="@string/cd_scan_preview"
            android:background="@drawable/bg_card_row"/>

        <!-- STATE: recognizing -->
        <LinearLayout
            android:id="@+id/scan_state_progress"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center_horizontal"
            android:visibility="gone">

            <ProgressBar
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="24dp"/>

            <TextView
                android:id="@+id/txt_scan_progress"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="@string/scan_recognizing"
                android:textColor="@color/text_secondary"/>
        </LinearLayout>

        <!-- STATE: result -->
        <LinearLayout
            android:id="@+id/scan_state_result"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:visibility="gone">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:text="@string/scan_brand_label"
                style="@style/SectionHeader"/>

            <EditText
                android:id="@+id/edit_brand"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="@drawable/bg_search_field"
                android:inputType="textCapWords"
                android:padding="14dp"/>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:text="@string/scan_substance_label"
                style="@style/SectionHeader"/>

            <TextView
                android:id="@+id/txt_substance"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:padding="14dp"
                android:background="@drawable/bg_card_row"
                android:textColor="@color/text_primary"
                android:textSize="16sp"
                android:textStyle="bold"/>

            <TextView
                android:id="@+id/txt_scan_warning"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:padding="14dp"
                android:background="@color/quote_background"
                android:text="@string/scan_warning_restricted"
                android:textColor="@color/status_penal"
                android:textStyle="bold"
                android:visibility="gone"/>

            <Button
                android:id="@+id/btn_scan_save"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="20dp"
                android:backgroundTint="@color/color_primary"
                android:text="@string/scan_save"
                android:textColor="@color/text_on_primary"/>

            <Button
                android:id="@+id/btn_scan_retry"
                style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="@string/scan_retry"
                android:textColor="@color/color_primary"/>
        </LinearLayout>

        <!-- STATE: error -->
        <LinearLayout
            android:id="@+id/scan_state_error"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center_horizontal"
            android:visibility="gone">

            <TextView
                android:id="@+id/txt_scan_error"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="24dp"
                android:gravity="center"
                android:textColor="@color/status_penal"
                android:textSize="15sp"/>

            <Button
                android:id="@+id/btn_error_retry"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:backgroundTint="@color/color_primary"
                android:text="@string/scan_retry"
                android:textColor="@color/text_on_primary"/>
        </LinearLayout>
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 4: Implement `ScanActivity`**

`app/src/main/java/com/davinci/medtraveler/ui/ScanActivity.java`:

```java
package com.davinci.medtraveler.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.davinci.medtraveler.R;
import com.davinci.medtraveler.data.AuthManager;
import com.davinci.medtraveler.data.CatalogRepo;
import com.davinci.medtraveler.data.DrugInfoRepo;
import com.davinci.medtraveler.data.UserMedsRepo;
import com.davinci.medtraveler.mlkit.OcrScanner;
import com.davinci.medtraveler.model.Medicine;
import com.davinci.medtraveler.util.SearchFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Photo → ML Kit OCR (on-device) → OpenFDA active-ingredient lookup → cross-check
 *  against the restriction catalog for the user's selected countries → save to the
 *  personal med list. One screen, four exclusive states: empty / progress / result / error. */
public class ScanActivity extends BaseActivity {

    private final OcrScanner ocr = new OcrScanner();
    private final DrugInfoRepo drugInfo = new DrugInfoRepo();
    private final AuthManager auth = new AuthManager();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private CatalogRepo catalog;
    private List<String> countryCodes = new ArrayList<>();

    private View stateEmpty, stateProgress, stateResult, stateError;
    private ImageView preview;
    private TextView progressText, substanceText, warningText, errorText;
    private EditText brandEdit;

    private Uri pendingCaptureUri;          // where the camera app writes the photo
    private String resolvedSubstance;       // null until OpenFDA answers
    private Medicine matchedCatalogMed;     // non-null when substance is in the catalog

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok && pendingCaptureUri != null) startRecognition(pendingCaptureUri);
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) startRecognition(uri);
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentWithChrome(R.layout.activity_scan);
        setTitle(R.string.scan_title);

        catalog = new CatalogRepo(this);
        String[] arr = getIntent().getStringArrayExtra(MedicineListActivity.EXTRA_COUNTRY_CODES);
        if (arr != null) countryCodes = new ArrayList<>(Arrays.asList(arr));

        stateEmpty = findViewById(R.id.scan_state_empty);
        stateProgress = findViewById(R.id.scan_state_progress);
        stateResult = findViewById(R.id.scan_state_result);
        stateError = findViewById(R.id.scan_state_error);
        preview = findViewById(R.id.img_scan_preview);
        progressText = findViewById(R.id.txt_scan_progress);
        substanceText = findViewById(R.id.txt_substance);
        warningText = findViewById(R.id.txt_scan_warning);
        errorText = findViewById(R.id.txt_scan_error);
        brandEdit = findViewById(R.id.edit_brand);

        findViewById(R.id.btn_take_photo).setOnClickListener(v -> launchCamera());
        findViewById(R.id.btn_pick_gallery).setOnClickListener(v -> pickImage.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        findViewById(R.id.btn_scan_retry).setOnClickListener(v -> showState(stateEmpty));
        findViewById(R.id.btn_error_retry).setOnClickListener(v -> showState(stateEmpty));
        findViewById(R.id.btn_scan_save).setOnClickListener(v -> save());

        showState(stateEmpty);
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "scans");
            if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("mkdirs failed");
            File photo = File.createTempFile("scan_", ".jpg", dir);
            pendingCaptureUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photo);
            takePicture.launch(pendingCaptureUri);
        } catch (Exception e) {
            Toast.makeText(this, R.string.scan_error_ocr, Toast.LENGTH_SHORT).show();
        }
    }

    private void startRecognition(Uri imageUri) {
        preview.setVisibility(View.VISIBLE);
        Glide.with(this).load(imageUri).into(preview);
        progressText.setText(R.string.scan_recognizing);
        showState(stateProgress);

        ocr.recognize(this, imageUri, new OcrScanner.ResultCallback() {
            @Override public void onResult(List<String> lines, String candidate) {
                if (candidate == null) { showError(getString(R.string.scan_error_ocr)); return; }
                brandEdit.setText(candidate);
                lookupSubstance(candidate);
            }
            @Override public void onError(Exception e) {
                showError(getString(R.string.scan_error_ocr));
            }
        });
    }

    private void lookupSubstance(String brand) {
        progressText.setText(R.string.scan_looking_up);
        showState(stateProgress);
        drugInfo.lookup(brand, new DrugInfoRepo.Callback() {
            @Override public void onFound(String activeIngredient) {
                resolvedSubstance = activeIngredient;
                substanceText.setText(activeIngredient);
                checkCatalog(activeIngredient);
            }
            @Override public void onNotFound() {
                resolvedSubstance = null;
                matchedCatalogMed = null;
                substanceText.setText(R.string.scan_substance_unknown);
                warningText.setVisibility(View.GONE);
                showState(stateResult);
            }
            @Override public void onError(Exception e) {
                showError(getString(R.string.scan_error_network));
            }
        });
    }

    /** Cross-checks the resolved substance against the cached restriction catalog,
     *  limited to the user's selected countries when provided. Room off the UI thread. */
    private void checkCatalog(String substance) {
        io.execute(() -> {
            List<Medicine> all = catalog.all();
            List<Medicine> scope = new ArrayList<>();
            for (Medicine m : all)
                if (countryCodes.isEmpty() || countryCodes.contains(m.countryCode))
                    scope.add(m);
            List<Medicine> hits = SearchFilter.filter(scope, substance);
            runOnUiThread(() -> {
                matchedCatalogMed = hits.isEmpty() ? null : hits.get(0);
                warningText.setVisibility(matchedCatalogMed == null ? View.GONE : View.VISIBLE);
                if (matchedCatalogMed != null) {
                    warningText.setOnClickListener(v -> {
                        Intent i = new Intent(this, MedicineDetailActivity.class);
                        i.putExtra(MedicineListActivity.EXTRA_MED_ID, matchedCatalogMed.id);
                        startActivity(i);
                    });
                }
                showState(stateResult);
            });
        });
    }

    private void save() {
        String uid = auth.currentUid();
        if (uid == null) {
            startActivity(new Intent(this, AuthActivity.class));
            return;
        }
        String brand = brandEdit.getText().toString().trim();
        new UserMedsRepo().addScannedMed(uid, brand, resolvedSubstance, ok -> runOnUiThread(() -> {
            if (ok) {
                Toast.makeText(this, R.string.scan_saved, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, R.string.scan_save_fail, Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void showError(String message) {
        errorText.setText(message);
        showState(stateError);
    }

    private void showState(View active) {
        for (View v : new View[]{stateEmpty, stateProgress, stateResult, stateError})
            v.setVisibility(v == active ? View.VISIBLE : View.GONE);
        if (active == stateEmpty) preview.setVisibility(View.GONE);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        drugInfo.shutdown();
        io.shutdown();
    }
}
```

**Caveat — `ActivityResultContracts.PickVisualMedia` needs `androidx.activity` ≥ 1.7.** `appcompat 1.7.0` pulls in an older transitive version on some setups. If Step 5's build fails on `PickVisualMedia`, add to `gradle/libs.versions.toml`: `activity = "1.9.3"` / `androidx-activity = { group = "androidx.activity", name = "activity", version.ref = "activity" }` and `implementation libs.androidx.activity` in `app/build.gradle`.

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL. If `PickVisualMedia` is unresolved, apply the caveat above and rebuild.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml app/src/main/res/layout/activity_scan.xml app/src/main/res/values/strings.xml app/src/main/java/com/davinci/medtraveler/ui/ScanActivity.java gradle/libs.versions.toml app/build.gradle
git commit -m "feat(scan): ScanActivity — photo/gallery, OCR, OpenFDA lookup, catalog check, save"
```

---

### Task 9: Entry points (drawer + toolbar camera icon) and Profile shows scanned meds

**Files:**
- Create: `app/src/main/res/drawable/ic_camera.xml`
- Modify: `app/src/main/res/menu/drawer_menu.xml`
- Modify: `app/src/main/res/menu/menu_update.xml`
- Modify: `app/src/main/java/com/davinci/medtraveler/ui/BaseActivity.java`
- Modify: `app/src/main/java/com/davinci/medtraveler/ui/MedicineListActivity.java`
- Modify: `app/src/main/java/com/davinci/medtraveler/ui/ProfileActivity.java`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `ScanActivity` (Task 8), `MyMed.activeSubstance` (Task 7).
- Produces: `R.id.nav_scan` drawer item on every screen; `R.id.action_scan` toolbar icon on the med list; Profile rows render `name — country` or `name — substance` for scanned meds.

- [ ] **Step 1: Camera icon vector**

`app/src/main/res/drawable/ic_camera.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="@color/text_on_primary">
    <path android:fillColor="#FFFFFF"
        android:pathData="M12,15.2A3.2,3.2 0,1 0,12 8.8A3.2,3.2 0,0 0,12 15.2ZM9,2L7.17,4L4,4C2.9,4 2,4.9 2,6L2,18C2,19.1 2.9,20 4,20L20,20C21.1,20 22,19.1 22,18L22,6C22,4.9 21.1,4 20,4L16.83,4L15,2L9,2ZM12,17A5,5 0,1 1,12 7A5,5 0,0 1,12 17Z"/>
</vector>
```

- [ ] **Step 2: Drawer item + string**

`drawer_menu.xml` — insert after `nav_search`:

```xml
    <item android:id="@+id/nav_scan" android:title="@string/menu_scan"/>
```

(String `menu_scan` already added in Task 8 Step 2.)

In `BaseActivity.java`, in the `setNavigationItemSelectedListener` lambda, add after the `nav_search` branch:

```java
            else if (id == R.id.nav_scan) startActivity(new Intent(this, ScanActivity.class));
```

- [ ] **Step 3: Toolbar camera action on the med list**

`menu_update.xml` — add before the update item:

```xml
    <item
        android:id="@+id/action_scan"
        android:title="@string/menu_scan"
        android:icon="@drawable/ic_camera"
        app:showAsAction="always" />
```

**Caveat:** `menu_update.xml` is inflated by `BaseActivity.onCreateOptionsMenu` for ALL screens, so the camera icon appears everywhere — acceptable and consistent (scan is globally useful). Handle the click in `BaseActivity.onOptionsItemSelected`, passing the country scope when available:

```java
        if (item.getItemId() == R.id.action_scan) {
            startActivity(scanIntent());
            return true;
        }
```

And add to `BaseActivity`:

```java
    /** Intent for the scan screen. Screens that know the selected countries override
     *  this to pass them along so the scan result is checked against the right catalog. */
    protected Intent scanIntent() {
        return new Intent(this, ScanActivity.class);
    }
```

In `MedicineListActivity`, override:

```java
    @Override protected Intent scanIntent() {
        Intent i = super.scanIntent();
        i.putExtra(EXTRA_COUNTRY_CODES, codes.toArray(new String[0]));
        return i;
    }
```

Also hide the toolbar scan icon on `ScanActivity` itself — add to `ScanActivity`:

```java
    @Override public boolean onCreateOptionsMenu(android.view.Menu menu) {
        super.onCreateOptionsMenu(menu);
        android.view.MenuItem scan = menu.findItem(R.id.action_scan);
        if (scan != null) scan.setVisible(false);
        return true;
    }
```

- [ ] **Step 4: Profile renders scanned meds**

In `ProfileActivity.render`, replace the name-binding line:

```java
            String country = it.countryCode == null ? "" : CountryCatalog.nameOf(it.countryCode);
            ((TextView) row.findViewById(R.id.txt_name)).setText(
                    getString(R.string.name_value_line, it.name, country));
```

with:

```java
            // Catalog meds show "name — country"; scanned meds have no country, show the
            // active substance instead (or just the name when substance is unknown).
            String secondary = it.countryCode != null ? CountryCatalog.nameOf(it.countryCode)
                    : (it.activeSubstance != null ? it.activeSubstance : "");
            ((TextView) row.findViewById(R.id.txt_name)).setText(secondary.isEmpty()
                    ? it.name : getString(R.string.name_value_line, it.name, secondary));
```

- [ ] **Step 5: Build + full tests**

Run: `./gradlew :app:assembleDebug -q && ./gradlew :app:testDebugUnitTest 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL both.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/drawable/ic_camera.xml app/src/main/res/menu/ app/src/main/java/com/davinci/medtraveler/ui/
git commit -m "feat(scan): drawer + toolbar entry points, profile shows scanned meds"
```

---

### Task 10: Localize all new strings (en/be/uk/zh; es is default)

**Files:**
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-es/strings.xml`
- Modify: `app/src/main/res/values-be/strings.xml`
- Modify: `app/src/main/res/values-uk/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Consumes: string names from Task 8 Step 2 (`menu_scan` … `cd_scan_preview`).
- Produces: fully localized scan UI in all 5 locales.

- [ ] **Step 1: Append translations to each locale file**

`values-en/strings.xml` (append before `</resources>`):

```xml
    <!-- Scan screen -->
    <string name="menu_scan">Scan medicine</string>
    <string name="scan_title">Scan medicine</string>
    <string name="scan_hint">Take a photo of the package to detect the active substance.</string>
    <string name="scan_take_photo">Take photo</string>
    <string name="scan_pick_gallery">Choose from gallery</string>
    <string name="scan_recognizing">Recognizing text…</string>
    <string name="scan_looking_up">Looking up active substance…</string>
    <string name="scan_brand_label">Detected name</string>
    <string name="scan_substance_label">Active substance</string>
    <string name="scan_substance_unknown">Not identified — edit the name and retry</string>
    <string name="scan_warning_restricted">Warning! This substance is listed for your destination countries. Tap to see details.</string>
    <string name="scan_save">I take this medicine</string>
    <string name="scan_retry">Retry</string>
    <string name="scan_error_ocr">Couldn\'t recognize text in the image</string>
    <string name="scan_error_network">Network error while looking up the substance</string>
    <string name="scan_saved">Saved to my medicines</string>
    <string name="scan_save_fail">Couldn\'t save</string>
    <string name="cd_scan_preview">Photo of the medicine</string>
```

`values-es/strings.xml` (append — same values as the defaults from Task 8, keeps the locale complete):

```xml
    <!-- Scan screen -->
    <string name="menu_scan">Escanear medicamento</string>
    <string name="scan_title">Escanear medicamento</string>
    <string name="scan_hint">Sacá una foto del envase para detectar la sustancia activa.</string>
    <string name="scan_take_photo">Sacar foto</string>
    <string name="scan_pick_gallery">Elegir de la galería</string>
    <string name="scan_recognizing">Reconociendo texto…</string>
    <string name="scan_looking_up">Buscando sustancia activa…</string>
    <string name="scan_brand_label">Nombre detectado</string>
    <string name="scan_substance_label">Sustancia activa</string>
    <string name="scan_substance_unknown">No identificada — podés editar el nombre y reintentar</string>
    <string name="scan_warning_restricted">¡Atención! Esta sustancia figura en el listado de tus países de destino. Tocá para ver el detalle.</string>
    <string name="scan_save">Tomo este medicamento</string>
    <string name="scan_retry">Reintentar</string>
    <string name="scan_error_ocr">No se pudo reconocer texto en la imagen</string>
    <string name="scan_error_network">Error de red al consultar la sustancia</string>
    <string name="scan_saved">Guardado en mis medicamentos</string>
    <string name="scan_save_fail">No se pudo guardar</string>
    <string name="cd_scan_preview">Foto del medicamento</string>
```

`values-be/strings.xml`:

```xml
    <!-- Scan screen -->
    <string name="menu_scan">Сканаваць лекі</string>
    <string name="scan_title">Сканаваць лекі</string>
    <string name="scan_hint">Сфатаграфуйце ўпакоўку, каб вызначыць дзейнае рэчыва.</string>
    <string name="scan_take_photo">Зрабіць фота</string>
    <string name="scan_pick_gallery">Выбраць з галерэі</string>
    <string name="scan_recognizing">Распазнаванне тэксту…</string>
    <string name="scan_looking_up">Пошук дзейнага рэчыва…</string>
    <string name="scan_brand_label">Распазнаная назва</string>
    <string name="scan_substance_label">Дзейнае рэчыва</string>
    <string name="scan_substance_unknown">Не вызначана — адрэдагуйце назву і паўтарыце</string>
    <string name="scan_warning_restricted">Увага! Гэтае рэчыва ёсць у спісе вашых краін прызначэння. Націсніце, каб убачыць падрабязнасці.</string>
    <string name="scan_save">Я прымаю гэтыя лекі</string>
    <string name="scan_retry">Паўтарыць</string>
    <string name="scan_error_ocr">Не ўдалося распазнаць тэкст на выяве</string>
    <string name="scan_error_network">Памылка сеткі пры пошуку рэчыва</string>
    <string name="scan_saved">Захавана ў маіх леках</string>
    <string name="scan_save_fail">Не ўдалося захаваць</string>
    <string name="cd_scan_preview">Фота лекаў</string>
```

`values-uk/strings.xml`:

```xml
    <!-- Scan screen -->
    <string name="menu_scan">Сканувати ліки</string>
    <string name="scan_title">Сканувати ліки</string>
    <string name="scan_hint">Сфотографуйте упаковку, щоб визначити діючу речовину.</string>
    <string name="scan_take_photo">Зробити фото</string>
    <string name="scan_pick_gallery">Вибрати з галереї</string>
    <string name="scan_recognizing">Розпізнавання тексту…</string>
    <string name="scan_looking_up">Пошук діючої речовини…</string>
    <string name="scan_brand_label">Розпізнана назва</string>
    <string name="scan_substance_label">Діюча речовина</string>
    <string name="scan_substance_unknown">Не визначено — відредагуйте назву і повторіть</string>
    <string name="scan_warning_restricted">Увага! Ця речовина є у списку ваших країн призначення. Торкніться, щоб побачити деталі.</string>
    <string name="scan_save">Я приймаю ці ліки</string>
    <string name="scan_retry">Повторити</string>
    <string name="scan_error_ocr">Не вдалося розпізнати текст на зображенні</string>
    <string name="scan_error_network">Помилка мережі під час пошуку речовини</string>
    <string name="scan_saved">Збережено в моїх ліках</string>
    <string name="scan_save_fail">Не вдалося зберегти</string>
    <string name="cd_scan_preview">Фото ліків</string>
```

`values-zh/strings.xml`:

```xml
    <!-- Scan screen -->
    <string name="menu_scan">扫描药品</string>
    <string name="scan_title">扫描药品</string>
    <string name="scan_hint">拍摄药盒照片以识别有效成分。</string>
    <string name="scan_take_photo">拍照</string>
    <string name="scan_pick_gallery">从相册选择</string>
    <string name="scan_recognizing">正在识别文字…</string>
    <string name="scan_looking_up">正在查询有效成分…</string>
    <string name="scan_brand_label">识别出的名称</string>
    <string name="scan_substance_label">有效成分</string>
    <string name="scan_substance_unknown">未能识别 — 可编辑名称后重试</string>
    <string name="scan_warning_restricted">注意！该成分在您目的地国家的管制清单中。点按查看详情。</string>
    <string name="scan_save">我服用此药</string>
    <string name="scan_retry">重试</string>
    <string name="scan_error_ocr">无法识别图片中的文字</string>
    <string name="scan_error_network">查询成分时网络出错</string>
    <string name="scan_saved">已保存到我的药品</string>
    <string name="scan_save_fail">保存失败</string>
    <string name="cd_scan_preview">药品照片</string>
```

- [ ] **Step 2: Build (lint catches missing-translation issues)**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values-en/ app/src/main/res/values-es/ app/src/main/res/values-be/ app/src/main/res/values-uk/ app/src/main/res/values-zh/
git commit -m "feat(l10n): scan-screen strings in en/es/be/uk/zh"
```

---

### Task 11: DrugInfoRepo network-path test with MockWebServer (androidTest)

**Files:**
- Test: `app/src/androidTest/java/com/davinci/medtraveler/data/DrugInfoRepoTest.java`

**Interfaces:**
- Consumes: `DrugInfoRepo.lookup/shutdown` and the package-private `baseUrl` hook (Task 6).
- Produces: instrumented coverage of found / not-found / server-error paths.

Why androidTest, not JVM test: `DrugInfoRepo` uses `android.os.Handler/Looper`, which JVM unit tests can't run without Robolectric (not in the stack). The project already has an instrumented-test harness with MockWebServer (`CatalogUpdaterTest`), so follow that pattern.

- [ ] **Step 1: Write the instrumented test**

`app/src/androidTest/java/com/davinci/medtraveler/data/DrugInfoRepoTest.java`:

```java
package com.davinci.medtraveler.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@RunWith(AndroidJUnit4.class)
public class DrugInfoRepoTest {

    private MockWebServer server;
    private DrugInfoRepo repo;
    private String originalBase;

    @Before public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        originalBase = DrugInfoRepo.baseUrl;
        DrugInfoRepo.baseUrl = server.url("/drug/label.json").toString();
        repo = new DrugInfoRepo();
    }

    @After public void tearDown() throws Exception {
        DrugInfoRepo.baseUrl = originalBase;
        repo.shutdown();
        server.shutdown();
    }

    @Test public void foundPathDeliversLowercaseIngredient() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"results\":[{\"openfda\":{\"generic_name\":[\"IBUPROFEN\"]}}]}"));
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> got = new AtomicReference<>();
        repo.lookup("Advil", new DrugInfoRepo.Callback() {
            @Override public void onFound(String s) { got.set(s); latch.countDown(); }
            @Override public void onNotFound() { latch.countDown(); }
            @Override public void onError(Exception e) { latch.countDown(); }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals("ibuprofen", got.get());
    }

    @Test public void openFda404ErrorBodyIsNotFound() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404).setBody(
                "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"No matches found!\"}}"));
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> outcome = new AtomicReference<>();
        repo.lookup("Nonexistium", new DrugInfoRepo.Callback() {
            @Override public void onFound(String s) { outcome.set("found"); latch.countDown(); }
            @Override public void onNotFound() { outcome.set("notfound"); latch.countDown(); }
            @Override public void onError(Exception e) { outcome.set("error"); latch.countDown(); }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals("notfound", outcome.get());
    }

    @Test public void blankQueryIsNotFoundWithoutNetworkCall() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> outcome = new AtomicReference<>();
        repo.lookup("   ", new DrugInfoRepo.Callback() {
            @Override public void onFound(String s) { outcome.set("found"); latch.countDown(); }
            @Override public void onNotFound() { outcome.set("notfound"); latch.countDown(); }
            @Override public void onError(Exception e) { outcome.set("error"); latch.countDown(); }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals("notfound", outcome.get());
        assertEquals(0, server.getRequestCount());
    }
}
```

- [ ] **Step 2: Compile the androidTest source set (no emulator needed to verify compilation)**

Run: `./gradlew :app:compileDebugAndroidTestSources -q`
Expected: BUILD SUCCESSFUL.

If a device/emulator is attached, optionally run: `./gradlew :app:connectedDebugAndroidTest --tests "com.davinci.medtraveler.data.DrugInfoRepoTest"` → 3 tests pass. If no device, note it in the commit body and move on.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/davinci/medtraveler/data/DrugInfoRepoTest.java
git commit -m "test(scan): instrumented DrugInfoRepo coverage via MockWebServer"
```

---

### Task 12: README update + final verification

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: everything above.
- Produces: documented feature + verified build.

- [ ] **Step 1: Update README**

In `README.md`:
- Add to the screen list (after section 6, before "7. Drawer"):

```markdown
### 7. ScanActivity (Escanear medicamento)

**Funcionalidades:** foto con la cámara del sistema (`TakePicture` + FileProvider,
sin permiso CAMERA) o imagen de la galería (`PickVisualMedia`); OCR on-device con
**Google ML Kit Text Recognition**; el nombre detectado se resuelve a **sustancia
activa** vía la API pública **OpenFDA** (`api.fda.gov/drug/label.json`, OkHttp);
si la sustancia figura en el catálogo de los países elegidos se muestra una
advertencia con link al detalle; "Tomo este medicamento" guarda en Firestore
`users/{uid}/myMeds/scan_<sustancia>` (requiere sesión).

**Flujo:** toolbar 📷 o drawer → **Escanear**; foto → reconocimiento → confirmación
(marca editable + sustancia) → guardar.
```

- Renumber the old "7. Drawer" section to 8, and add `Escanear` to the drawer items list in that section.
- In **Stack**, append: `· ML Kit Text Recognition (OCR on-device) · OpenFDA drug/label API`.
- In the flow diagram at the top, add a `→ Escanear (OCR)` branch under the drawer line.
- Add one line to the design note: the UI uses a Belarusian *vyshyvanka* visual theme (white/red, folk diamond ornament).

- [ ] **Step 2: Final full verification**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -5
./gradlew :app:assembleDebug -q
./gradlew :app:compileDebugAndroidTestSources -q
```
Expected: all BUILD SUCCESSFUL; unit suite green.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: document scan feature and vyshyvanka redesign in README"
```
