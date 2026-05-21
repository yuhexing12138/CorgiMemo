package com.corgimemo.app.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "category_keywords",
    indices = [
        Index(value = ["keyword"], unique = false),
        Index(value = ["categoryType"])
    ]
)
data class CategoryKeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String,
    val categoryType: Int,
    val matchType: Int,
    val isUserDefined: Boolean
)
