package com.mobiladorprox.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM game_profiles ORDER BY gameType ASC, createdAt ASC")
    fun observeProfiles(): Flow<List<GameProfileEntity>>

    @Query("SELECT * FROM game_profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfile(profileId: Long): GameProfileEntity?

    @Query("SELECT * FROM game_profiles WHERE id = :profileId LIMIT 1")
    fun observeProfile(profileId: Long): Flow<GameProfileEntity?>

    @Query("SELECT COUNT(*) FROM game_profiles")
    suspend fun countProfiles(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: GameProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: GameProfileEntity)

    @Query("DELETE FROM game_profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: Long)

    @Query("SELECT * FROM key_bindings WHERE profileId = :profileId ORDER BY id ASC")
    fun observeBindings(profileId: Long): Flow<List<KeyBindingEntity>>

    @Query("SELECT * FROM key_bindings WHERE profileId = :profileId ORDER BY id ASC")
    suspend fun getBindingsOnce(profileId: Long): List<KeyBindingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBindings(bindings: List<KeyBindingEntity>)

    @Update
    suspend fun updateBinding(binding: KeyBindingEntity)

    @Query("DELETE FROM key_bindings WHERE profileId = :profileId")
    suspend fun deleteBindingsForProfile(profileId: Long)
}
