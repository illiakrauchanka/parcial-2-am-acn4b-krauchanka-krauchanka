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
