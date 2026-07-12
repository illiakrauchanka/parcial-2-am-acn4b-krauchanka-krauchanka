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
