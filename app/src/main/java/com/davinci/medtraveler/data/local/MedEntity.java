package com.davinci.medtraveler.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "meds")
public class MedEntity {
    @PrimaryKey @NonNull public String id = "";
    public String name;
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
