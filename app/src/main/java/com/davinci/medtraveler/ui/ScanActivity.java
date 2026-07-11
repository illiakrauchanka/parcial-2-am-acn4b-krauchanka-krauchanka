package com.davinci.medtraveler.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
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
