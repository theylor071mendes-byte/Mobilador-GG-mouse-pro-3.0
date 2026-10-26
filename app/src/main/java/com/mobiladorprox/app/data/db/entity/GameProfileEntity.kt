package com.mobiladorprox.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Um perfil de keymap salvo para um jogo instalado específico. */
@Entity(tableName = "game_profiles")
data class GameProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileName: String,
    val packageName: String,
    val gameLabel: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
