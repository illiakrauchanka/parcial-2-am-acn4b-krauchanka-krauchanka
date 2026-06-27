package com.davinci.medtraveler.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface MedDao {
    @Query("SELECT * FROM meds WHERE countryCode = :code ORDER BY name")
    List<MedEntity> byCountry(String code);

    @Query("SELECT * FROM meds WHERE id = :id LIMIT 1")
    MedEntity byId(String id);

    @Query("SELECT * FROM meds ORDER BY name")
    List<MedEntity> all();

    @Query("DELETE FROM meds WHERE countryCode = :code")
    void deleteByCountry(String code);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MedEntity> meds);

    /** Atomic swap so a concurrent reader never sees a country mid-delete. */
    @Transaction
    default void replaceCountry(String code, List<MedEntity> items) {
        deleteByCountry(code);
        insertAll(items);
    }
}
