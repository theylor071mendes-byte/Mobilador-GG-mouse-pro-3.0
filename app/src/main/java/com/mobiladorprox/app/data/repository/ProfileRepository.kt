package com.mobiladorprox.app.data.repository

import com.mobiladorprox.app.data.db.ProfileDao
import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import com.mobiladorprox.app.data.model.BindingTemplate
import com.mobiladorprox.app.data.model.DefaultProfiles
import com.mobiladorprox.app.data.model.GestureType
import kotlinx.coroutines.flow.Flow

/**
 * Ponto único de acesso aos dados de perfis (keymaps) e vínculos de tecla.
 * Toda a persistência é local (Room) e sobrevive ao fechamento do app.
 */
class ProfileRepository(private val dao: ProfileDao) {

    fun observeProfiles(): Flow<List<GameProfileEntity>> = dao.observeProfiles()

    fun observeProfilesForPackage(packageName: String): Flow<List<GameProfileEntity>> =
        dao.observeProfilesForPackage(packageName)

    fun observeBindings(profileId: Long): Flow<List<KeyBindingEntity>> = dao.observeBindings(profileId)

    suspend fun getProfile(profileId: Long): GameProfileEntity? = dao.getProfile(profileId)

    fun observeProfile(profileId: Long): Flow<GameProfileEntity?> = dao.observeProfile(profileId)

    suspend fun getBindingsOnce(profileId: Long): List<KeyBindingEntity> = dao.getBindingsOnce(profileId)

    /** Retorna o primeiro perfil salvo para o jogo, criando um com os padrões se não existir nenhum. */
    suspend fun getOrCreateProfileForGame(packageName: String, gameLabel: String): GameProfileEntity {
        dao.getFirstProfileForPackage(packageName)?.let { return it }
        val id = createProfile("Padrão", packageName, gameLabel)
        return dao.getProfile(id)!!
    }

    suspend fun createProfile(profileName: String, packageName: String, gameLabel: String): Long {
        val entity = GameProfileEntity(
            profileName = profileName,
            packageName = packageName,
            gameLabel = gameLabel
        )
        val newId = dao.insertProfile(entity)
        seedBindings(newId, DefaultProfiles.defaultBindings())
        return newId
    }

    private suspend fun seedBindings(profileId: Long, templates: List<BindingTemplate>) {
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
            profileName = newName,
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
        dao.updateProfile(profile.copy(profileName = newName, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProfile(profileId: Long) {
        dao.deleteProfile(profileId)
    }

    /** Restaura os vínculos de tecla do perfil para o conjunto padrão (apaga os atuais). */
    suspend fun restoreDefaults(profileId: Long) {
        dao.deleteBindingsForProfile(profileId)
        seedBindings(profileId, DefaultProfiles.defaultBindings())
        dao.updateProfile(
            (dao.getProfile(profileId) ?: return).copy(updatedAt = System.currentTimeMillis())
        )
    }

    /** Substitui todos os vínculos do perfil pelo conjunto informado (usado por "Salvar configuração"). */
    suspend fun replaceBindings(profileId: Long, bindings: List<KeyBindingEntity>) {
        dao.deleteBindingsForProfile(profileId)
        if (bindings.isNotEmpty()) {
            dao.insertBindings(bindings.map { it.copy(id = 0, profileId = profileId) })
        }
        val profile = dao.getProfile(profileId) ?: return
        dao.updateProfile(profile.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun addBinding(
        profileId: Long,
        actionId: String,
        label: String,
        keyCode: Int,
        gestureType: GestureType,
        xPercent: Float,
        yPercent: Float
    ): Long {
        return dao.insertBinding(
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
    }
}
