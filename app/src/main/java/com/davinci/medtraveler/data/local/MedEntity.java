package com.davinci.medtraveler.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "meds")
public class MedEntity {
    @PrimaryKey @NonNull public String id = "";
    /** Localized medicine name in the language the catalog was last fetched/seeded for
     *  (es / en / uk / be / zh). Chosen from {@code names[lang]} by CatalogJson. */
    public String name;

    /** International Nonproprietary Name (INN) — Latin, language-independent. Always
     *  available so the user can identify the substance even when the UI language and
     *  the catalog language get out of sync. */
    public String nameLatin;

    public String countryCode;
    public String status;          // "RESTRICTED" | "PENAL"
    public String activeSubstance;
    @ColumnInfo(name = "group_") public String group;  // Java field is `group`; column is `group_`
    public String prescription;
    public String brand;
    public String description;
    public String lawExcerpt;
    public String penalty;
    public String sourceUrl;
    public String imageUrl;
}