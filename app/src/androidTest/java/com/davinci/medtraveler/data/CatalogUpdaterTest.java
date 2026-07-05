package com.davinci.medtraveler.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Instrumented test for {@link CatalogUpdater} against a {@link MockWebServer}. Runs on a
 * device/emulator because CatalogUpdater needs a real Context (SharedPreferences via
 * CatalogMeta) and the real Room singleton (CatalogRepo writes through {@code
 * AppDatabase.get}). We deliberately test ONLY the HTTP/caching callback behaviour and the
 * request path here — downstream Room persistence is covered by {@link MedDaoTest}.
 *
 * The package-private two-arg constructor {@code new CatalogUpdater(ctx, baseUrl)} is the
 * only test seam we added; production callers still use {@code new CatalogUpdater(ctx)}
 * which defaults to {@link Catalog#RTDB_BASE}.
 */
@RunWith(AndroidJUnit4.class)
public class CatalogUpdaterTest {

    private MockWebServer server;
    private CatalogUpdater updater;

    @Before public void setUp() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        server = new MockWebServer();
        server.start();
        updater = new CatalogUpdater(ctx, server.url("/").toString());
    }

    @After public void tearDown() throws Exception {
        updater.shutdown();
        if (server != null) server.shutdown();
    }

    private static final String AR_BODY =
            "{ \"version\": 5, \"updatedAt\": \"2026-07-04T00:00:00Z\", \"items\": ["
          + "  { \"id\": \"ar-tramadol\", \"name\": \"Tramadol\", \"status\": \"PENAL\","
          + "    \"activeSubstance\": \"x\", \"group\": \"g\", \"prescription\": \"p\","
          + "    \"brand\": \"b\", \"description\": \"d\", \"lawExcerpt\": \"l\","
          + "    \"penalty\": \"pen\", \"sourceUrl\": \"u\", \"imageUrl\": \"img\" }"
          + "] }";

    @Test public void refreshCountries_success_requestsCorrectPathAndReportsOne() throws Exception {
        server.enqueue(new MockResponse().setBody(AR_BODY).setResponseCode(200));

        CountDownLatch latch = new CountDownLatch(1);
        int[] done = new int[]{-1, -1};
        updater.refreshCountries(Arrays.asList("AR"), true, (u, t) -> {
            done[0] = u; done[1] = t; latch.countDown();
        });
        assertTrue("callback never fired", latch.await(5, TimeUnit.SECONDS));

        assertEquals(1, done[0]);   // 1 country downloaded
        assertEquals(1, done[1]);   // 1 total requested

        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("/meds/AR.json", req.getPath());
        assertEquals("GET", req.getMethod());
    }

    @Test public void refreshCountries_404_reportsZero() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));
        CountDownLatch latch = new CountDownLatch(1);
        int[] done = new int[]{99, 99};
        updater.refreshCountries(Arrays.asList("AR"), true, (u, t) -> {
            done[0] = u; done[1] = t; latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(0, done[0]);   // nothing downloaded
        assertEquals(1, done[1]);   // but AR was still requested
    }

    @Test public void refreshCountries_nullBody_reportsZero() throws Exception {
        // RTDB returns literal "null" when a path has no data — updater must treat that as
        // "nothing to download", not a parse error.
        server.enqueue(new MockResponse().setBody("null").setResponseCode(200));
        CountDownLatch latch = new CountDownLatch(1);
        int[] done = new int[]{99, 99};
        updater.refreshCountries(Arrays.asList("AR"), true, (u, t) -> {
            done[0] = u; done[1] = t; latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(0, done[0]);
        assertEquals(1, done[1]);
    }

    @Test public void refreshCountries_emptyBody_reportsZero() throws Exception {
        server.enqueue(new MockResponse().setBody("").setResponseCode(200));
        CountDownLatch latch = new CountDownLatch(1);
        int[] done = new int[]{99, 99};
        updater.refreshCountries(Arrays.asList("AR"), true, (u, t) -> {
            done[0] = u; done[1] = t; latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(0, done[0]);
        assertEquals(1, done[1]);
    }

    @Test public void refreshCountries_multipleCountries_multipleRequests() throws Exception {
        server.enqueue(new MockResponse().setBody(AR_BODY).setResponseCode(200));
        server.enqueue(new MockResponse().setBody("null").setResponseCode(200));

        CountDownLatch latch = new CountDownLatch(1);
        int[] done = new int[]{99, 99};
        updater.refreshCountries(Arrays.asList("AR", "JP"), true, (u, t) -> {
            done[0] = u; done[1] = t; latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, done[0]);   // only AR had real data
        assertEquals(2, done[1]);
        assertEquals(2, server.getRequestCount());
    }
}