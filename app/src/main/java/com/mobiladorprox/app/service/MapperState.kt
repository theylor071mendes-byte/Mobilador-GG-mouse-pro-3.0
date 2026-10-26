package com.mobiladorprox.app.service

import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import com.mobiladorprox.app.data.model.GestureType

/**
 * Estado em memória do perfil (keymap) atualmente carregado e das
 * preferências globais de mouse/desempenho. É alimentado pelo
 * [OverlayService] (que lê o banco local e o DataStore) e consumido pelo
 * [MapperAccessibilityService] (que não acessa persistência diretamente,
 * apenas reage a eventos de tecla).
 */
object MapperState {

    @Volatile
    var isMapperActive: Boolean = false

    @Volatile
    var isCameraCaptureActive: Boolean = false

    @Volatile
    var activeProfileId: Long? = null

    @Volatile
    var activeProfileName: String = ""

    @Volatile
    var bindingsByKeyCode: Map<Int, KeyBindingEntity> = emptyMap()

    @Volatile
    var cameraBinding: KeyBindingEntity? = null

    /** Vínculo do analógico virtual (WASD combinado), se o perfil tiver um. */
    @Volatile
    var analogStickBinding: KeyBindingEntity? = null

    /** Multiplicador global de sensibilidade do mouse (1.00 a 20.00), configurado em "Configurações do mouse". */
    @Volatile
    var mouseSensitivity: Float = 5f

    /** 1f = resposta crua; valores menores suavizam o movimento da câmera. Vem do Perfil de desempenho. */
    @Volatile
    var lookResponsiveness: Float = 0.65f

    fun loadProfile(profile: GameProfileEntity, bindings: List<KeyBindingEntity>) {
        activeProfileId = profile.id
        activeProfileName = profile.profileName
        bindingsByKeyCode = bindings
            .filter { it.gestureType != GestureType.LOOK_TOGGLE.name && it.gestureType != GestureType.ANALOG_STICK.name }
            .associateBy { it.keyCode }
        cameraBinding = bindings.firstOrNull { it.gestureType == GestureType.LOOK_TOGGLE.name }
        analogStickBinding = bindings.firstOrNull { it.gestureType == GestureType.ANALOG_STICK.name }
    }

    fun clear() {
        isMapperActive = false
        isCameraCaptureActive = false
        activeProfileId = null
        activeProfileName = ""
        bindingsByKeyCode = emptyMap()
        cameraBinding = null
        analogStickBinding = null
    }
}
