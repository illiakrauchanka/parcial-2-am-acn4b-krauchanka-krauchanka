package com.davinci.medtraveler;

import static org.junit.Assert.assertEquals;

import com.davinci.medtraveler.model.Status;
import org.junit.Test;

/**
 * Edge-case coverage for {@link Status#fromString} that complements StatusTest:
 * the implementation calls {@code raw.trim()} before matching, but the existing
 * tests never feed whitespace-padded input. We lock that behaviour down here so a
 * future refactor doesn't silently break "  PENAL  " coming from a JSON field that
 * wasn't trimmed upstream.
 */
public class StatusEdgeTest {

    @Test public void trimsAndMatches() {
        assertEquals(Status.PENAL,     Status.fromString("  PENAL  "));
        assertEquals(Status.ALLOWED,   Status.fromString("\tALLOWED\n"));
        assertEquals(Status.RESTRICTED, Status.fromString("   RESTRICTED   "));
    }

    @Test public void blankString_defaultsToRestricted() {
        // "" or whitespace-only must not crash and must not match ALLOWED/PENAL.
        assertEquals(Status.RESTRICTED, Status.fromString(""));
        assertEquals(Status.RESTRICTED, Status.fromString("   "));
    }
}