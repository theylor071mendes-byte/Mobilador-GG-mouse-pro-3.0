package com.mobiladorprox.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_profiles")
data class GameProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val gameType: String,
    val sensitivityHorizontal: Float,
    val sensitivityVertical: Float,
    val cursorSpeed: Float,
    val dpiMultiplier: Float,
    val presetMode: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
