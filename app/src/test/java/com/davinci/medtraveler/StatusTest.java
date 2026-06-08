package com.davinci.medtraveler;

import static org.junit.Assert.assertEquals;

import com.davinci.medtraveler.model.Status;
import org.junit.Test;

public class StatusTest {
    @Test public void parsesKnownValues() {
        assertEquals(Status.ALLOWED, Status.fromString("ALLOWED"));
        assertEquals(Status.RESTRICTED, Status.fromString("RESTRICTED"));
        assertEquals(Status.PENAL, Status.fromString("PENAL"));
    }
    @Test public void isCaseInsensitive() {
        assertEquals(Status.PENAL, Status.fromString("penal"));
    }
    @Test public void unknownDefaultsToRestricted() {
        assertEquals(Status.RESTRICTED, Status.fromString("xxx"));
        assertEquals(Status.RESTRICTED, Status.fromString(null));
    }
}
