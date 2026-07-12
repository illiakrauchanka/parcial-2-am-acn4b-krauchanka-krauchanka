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
