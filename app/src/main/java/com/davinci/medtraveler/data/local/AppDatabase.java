package com.davinci.medtraveler.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {MedEntity.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    public abstract MedDao medDao();

    /**
     * v1 -> v2: added the {@code nameLatin} column to {@code meds}. Existing rows get NULL
     * for nameLatin; CatalogRepo re-seeds when it detects the column is empty or the
     * seeded language no longer matches the UI language, so this is a non-destructive
     * migration (no data has to move, no schema reset that would drop the user's saved
     * catalog cache).
     */
    static final Migration M1_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE meds ADD COLUMN nameLatin TEXT");
        }
    };

    public static AppDatabase get(Context ctx) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(ctx.getApplicationContext(),
                                    AppDatabase.class, "medtraveler.db")
                            .addMigrations(M1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}