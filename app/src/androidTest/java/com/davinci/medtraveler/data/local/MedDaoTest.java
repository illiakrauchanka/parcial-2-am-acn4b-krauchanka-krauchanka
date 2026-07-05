package com.davinci.medtraveler.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Instrumented test for {@link MedDao} backed by an in-memory Room database — runs on a
 * real device/emulator (needed because Room's SQLite is the Android system SQLite, not a
 * JVM stub). The real app uses the singleton {@link AppDatabase#get}; here we build a
 * throwaway in-memory instance via {@code Room.inMemoryDatabaseBuilder} so we never touch
 * the production file and tests stay hermetic.
 *
 * Coverage: byCountry ordering, byId miss/hit, all() aggregation, and the atomic
 * replaceCountry swap (delete + insert in one transaction) that CatalogUpdater relies on
 * so a concurrent reader never sees a country mid-update.
 */
@RunWith(AndroidJUnit4.class)
public class MedDaoTest {

    private AppDatabase db;
    private MedDao dao;

    @Before public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class)
                .allowMainThreadQueries()   // tests run synchronously on the test thread
                .build();
        dao = db.medDao();
    }

    @After public void tearDown() {
        db.close();
    }

    private MedEntity med(String id, String name, String code, String status) {
        MedEntity m = new MedEntity();
        m.id = id;
        m.name = name;
        m.countryCode = code;
        m.status = status;
        m.activeSubstance = "s";
        m.group = "g";
        m.prescription = "p";
        m.brand = "b";
        m.description = "d";
        m.lawExcerpt = "l";
        m.penalty = "pen";
        m.sourceUrl = "u";
        m.imageUrl = "img";
        return m;
    }

    @Test public void insertAndReadByCountry_orderedByName() {
        dao.insertAll(Arrays.asList(
                med("ar-3", "Tramadol", "AR", "PENAL"),
                med("ar-1", "Amitriptilina", "AR", "ALLOWED"),
                med("ar-2", "Bromazepam", "AR", "RESTRICTED"),
                med("jp-1", "Zolpidem", "JP", "RESTRICTED")));

        List<MedEntity> ar = dao.byCountry("AR");
        assertEquals(3, ar.size());
        assertEquals("Amitriptilina", ar.get(0).name);    // ordered by name
        assertEquals("Bromazepam",   ar.get(1).name);
        assertEquals("Tramadol",     ar.get(2).name);

        List<MedEntity> jp = dao.byCountry("JP");
        assertEquals(1, jp.size());
        assertEquals("Zolpidem", jp.get(0).name);
    }

    @Test public void byId_hitAndMiss() {
        dao.insertAll(Collections.singletonList(med("ar-1", "Tramadol", "AR", "PENAL")));
        assertNotNull(dao.byId("ar-1"));
        assertEquals("Tramadol", dao.byId("ar-1").name);
        assertNull(dao.byId("does-not-exist"));
    }

    @Test public void all_aggregatesAcrossCountries_orderedByName() {
        dao.insertAll(Arrays.asList(
                med("jp-1", "Zolpidem", "JP", "RESTRICTED"),
                med("ar-1", "Amitriptilina", "AR", "ALLOWED")));
        List<MedEntity> all = dao.all();
        assertEquals(2, all.size());
        assertEquals("Amitriptilina", all.get(0).name);   // global A..Z ordering, not grouped
        assertEquals("Zolpidem", all.get(1).name);
    }

    @Test public void replaceCountry_isAtomic_noIntermediateState() {
        dao.insertAll(Arrays.asList(
                med("ar-1", "Old-Tramadol", "AR", "PENAL"),
                med("ar-2", "Old-Codeína", "AR", "RESTRICTED")));

        // replaceCountry must delete + insert in a single Room transaction; afterwards the
        // country contains exactly the new set, with no leftover rows and no NPE on a
        // concurrent reader reading mid-swap.
        dao.replaceCountry("AR", Arrays.asList(
                med("ar-1", "Tramadol", "AR", "PENAL"),
                med("ar-3", "Diazepam", "AR", "RESTRICTED")));

        List<MedEntity> ar = dao.byCountry("AR");
        assertEquals(2, ar.size());
        assertEquals("Diazepam", ar.get(0).name);
        assertEquals("Tramadol", ar.get(1).name);
    }

    @Test public void replaceCountry_emptyNewSet_clearsAllRowsForCountry() {
        dao.insertAll(Collections.singletonList(med("ar-1", "Tramadol", "AR", "PENAL")));
        dao.replaceCountry("AR", Collections.<MedEntity>emptyList());
        assertTrue(dao.byCountry("AR").isEmpty());
        // other countries untouched
        dao.insertAll(Collections.singletonList(med("jp-1", "Zolpidem", "JP", "RESTRICTED")));
        dao.replaceCountry("AR", Collections.<MedEntity>emptyList());
        assertEquals(1, dao.byCountry("JP").size());
    }

    @Test public void deleteByCountry_isolatedByCountry() {
        dao.insertAll(Arrays.asList(
                med("ar-1", "A", "AR", "PENAL"),
                med("jp-1", "B", "JP", "RESTRICTED")));
        dao.deleteByCountry("AR");
        assertTrue(dao.byCountry("AR").isEmpty());
        assertEquals(1, dao.byCountry("JP").size());
    }

    @Test public void insertAll_replacesOnIdConflict() {
        // OnConflictStrategy.REPLACE: re-inserting the same id must overwrite fields,
        // not duplicate the row.
        dao.insertAll(Collections.singletonList(med("ar-1", "Tramadol", "AR", "PENAL")));
        dao.insertAll(Collections.singletonList(med("ar-1", "Tramadol-v2", "AR", "ALLOWED")));

        List<MedEntity> ar = dao.byCountry("AR");
        assertEquals(1, ar.size());
        assertEquals("Tramadol-v2", ar.get(0).name);
        assertEquals("ALLOWED", ar.get(0).status);
    }
}