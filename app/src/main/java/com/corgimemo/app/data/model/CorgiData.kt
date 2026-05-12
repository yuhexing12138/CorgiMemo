package com.corgimemo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "corgi_data")
data class CorgiData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val level: Int = 1,
    val experience: Int = 0,
    val currentOutfit: String? = null,
    val unlockedOutfits: String = "[]",
    val moodValue: Int = 50,
    val lastActiveDate: String,
    val totalCompleted: Int = 0,
    val consecutiveDays: Int = 0
)
