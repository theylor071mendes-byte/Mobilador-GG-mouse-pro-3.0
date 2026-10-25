package com.mobiladorprox.app.data.repository

import com.mobiladorprox.app.data.db.ProfileDao
import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import com.mobiladorprox.app.data.model.DefaultProfiles
import com.mobiladorprox.app.data.model.GameType
import com.mobiladorprox.app.data.model.PresetMode
import kotlinx.coroutines.flow.Flow

/**
 * Ponto único de acesso aos dados de perfis e vínculos de tecla.
 * Toda a persistência é local (Room) e sobrevive ao fechamento do app.
 */
class ProfileRepository(private val dao: ProfileDao) {

    fun observeProfiles(): Flow<List<GameProfileEntity>> = dao.observeProfiles()

    fun observeBindings(profileId: Long): Flow<List<KeyBindingEntity>> =
        dao.observeBindings(profileId)

    suspend fun getProfile(profileId: Long): GameProfileEntity? = dao.getProfile(profileId)

    fun observeProfile(profileId: Long): Flow<GameProfileEntity?> = dao.observeProfile(profileId)

    suspend fun getBindingsOnce(profileId: Long): List<KeyBindingEntity> =
        dao.getBindingsOnce(profileId)

    /** Cria os três perfis padrão na primeira execução do app, se ainda não existirem. */
    suspend fun seedDefaultProfilesIfEmpty() {
        if (dao.countProfiles() > 0) return
        createProfile("Free Fire", GameType.FREE_FIRE, PresetMode.EQUILIBRIO, isDefault = true)
        createProfile("Outro jogo (exemplo)", GameType.OUTRO_JOGO, PresetMode.EQUILIBRIO, isDefault = true)
        createProfile("Personalizado", GameType.PERSONALIZADO, PresetMode.PERSONALIZADO, isDefault = true)
    }

    suspend fun createProfile(
        name: String,
        gameType: GameType,
        preset: PresetMode = PresetMode.EQUILIBRIO,
        isDefault: Boolean = false
    ): Long {
        val values = DefaultProfiles.presetValues(preset)
        val entity = GameProfileEntity(
            name = name,
            gameType = gameType.name,
            sensitivityHorizontal = values.sensitivityHorizontal,
            sensitivityVertical = values.sensitivityVertical,
            cursorSpeed = values.cursorSpeed,
            dpiMultiplier = values.dpiMultiplier,
            presetMode = preset.name,
            isDefault = isDefault
        )
        val newId = dao.insertProfile(entity)
        seedBindings(newId, gameType)
        return newId
    }

    private suspend fun seedBindings(profileId: Long, gameType: GameType) {
        val templates = DefaultProfiles.bindingsFor(gameType)
        if (templates.isEmpty()) return
        val bindings = templates.map {
            KeyBindingEntity(
                profileId = profileId,
                actionId = it.actionId,
                label = it.label,
                keyCode = it.keyCode,
                gestureType = it.gestureType.name,
                xPercent = it.xPercent,
                yPercent = it.yPercent
            )
        }
        dao.insertBindings(bindings)
    }

    suspend fun duplicateProfile(profileId: Long, newName: String): Long? {
        val original = dao.getProfile(profileId) ?: return null
        val copy = original.copy(
            id = 0,
            name = newName,
            isDefault = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val newId = dao.insertProfile(copy)
        val originalBindings = dao.getBindingsOnce(profileId)
        if (originalBindings.isNotEmpty()) {
            dao.insertBindings(originalBindings.map { it.copy(id = 0, profileId = newId) })
        }
        return newId
    }

    suspend fun renameProfile(profileId: Long, newName: String) {
        val profile = dao.getProfile(profileId) ?: return
        dao.updateProfile(profile.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProfile(profileId: Long) {
        dao.deleteProfile(profileId)
    }

    suspend fun updateProfileSettings(
        profileId: Long,
        sensitivityHorizontal: Float,
        sensitivityVertical: Float,
        cursorSpeed: Float,
        dpiMultiplier: Float,
        presetMode: PresetMode
    ) {
        val profile = dao.getProfile(profileId) ?: return
        dao.updateProfile(
            profile.copy(
                sensitivityHorizontal = sensitivityHorizontal,
                sensitivityVertical = sensitivityVertical,
                cursorSpeed = cursorSpeed,
                dpiMultiplier = dpiMultiplier,
                presetMode = presetMode.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun applyPreset(profileId: Long, preset: PresetMode) {
        val profile = dao.getProfile(profileId) ?: return
        val values = DefaultProfiles.presetValues(preset)
        dao.updateProfile(
            profile.copy(
                sensitivityHorizontal = values.sensitivityHorizontal,
                sensitivityVertical = values.sensitivityVertical,
                cursorSpeed = values.cursorSpeed,
                dpiMultiplier = values.dpiMultiplier,
                presetMode = preset.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Restaura sensibilidade e vínculos de tecla para os valores padrão do tipo de jogo. */
    suspend fun restoreDefaults(profileId: Long) {
        val profile = dao.getProfile(profileId) ?: return
        val gameType = runCatching { GameType.valueOf(profile.gameType) }.getOrDefault(GameType.PERSONALIZADO)
        val values = DefaultProfiles.presetValues(PresetMode.EQUILIBRIO)
        dao.updateProfile(
            profile.copy(
                sensitivityHorizontal = values.sensitivityHorizontal,
                sensitivityVertical = values.sensitivityVertical,
                cursorSpeed = values.cursorSpeed,
                dpiMultiplier = values.dpiMultiplier,
                presetMode = PresetMode.EQUILIBRIO.name,
                updatedAt = System.currentTimeMillis()
            )
        )
        dao.deleteBindingsForProfile(profileId)
        seedBindings(profileId, gameType)
    }

    suspend fun updateBinding(binding: KeyBindingEntity) {
        dao.updateBinding(binding)
    }

    suspend fun addBinding(
        profileId: Long,
        actionId: String,
        label: String,
        keyCode: Int,
        gestureType: com.mobiladorprox.app.data.model.GestureType,
        xPercent: Float,
        yPercent: Float
    ) {
        dao.insertBindings(
            listOf(
                KeyBindingEntity(
                    profileId = profileId,
                    actionId = actionId,
                    label = label,
                    keyCode = keyCode,
                    gestureType = gestureType.name,
                    xPercent = xPercent,
                    yPercent = yPercent
                )
            )
        )
    }
}
