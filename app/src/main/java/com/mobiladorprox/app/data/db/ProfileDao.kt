package com.mobiladorprox.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM game_profiles ORDER BY gameLabel ASC, createdAt ASC")
    fun observeProfiles(): Flow<List<GameProfileEntity>>

    @Query("SELECT * FROM game_profiles WHERE packageName = :packageName ORDER BY createdAt ASC")
    fun observeProfilesForPackage(packageName: String): Flow<List<GameProfileEntity>>

    @Query("SELECT * FROM game_profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfile(profileId: Long): GameProfileEntity?

    @Query("SELECT * FROM game_profiles WHERE id = :profileId LIMIT 1")
    fun observeProfile(profileId: Long): Flow<GameProfileEntity?>

    @Query("SELECT * FROM game_profiles WHERE packageName = :packageName ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirstProfileForPackage(packageName: String): GameProfileEntity?

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBinding(binding: KeyBindingEntity): Long

    @Query("DELETE FROM key_bindings WHERE profileId = :profileId")
    suspend fun deleteBindingsForProfile(profileId: Long)

    @Query("DELETE FROM key_bindings WHERE id = :bindingId")
    suspend fun deleteBinding(bindingId: Long)
}
