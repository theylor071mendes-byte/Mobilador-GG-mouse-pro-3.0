package com.mobiladorprox.app.service

import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import com.mobiladorprox.app.data.model.GestureType

/**
 * Estado em memória do perfil atualmente carregado para mapeamento.
 * É alimentado pelo [com.mobiladorprox.app.service.OverlayService] (que lê o
 * perfil do banco local) e consumido pelo [MapperAccessibilityService]
 * (que não acessa o banco diretamente, apenas reage a eventos de tecla).
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

    @Volatile
    var sensitivityHorizontal: Float = 0.6f

    @Volatile
    var sensitivityVertical: Float = 0.55f

    @Volatile
    var dpiMultiplier: Float = 1.4f

    fun loadProfile(profile: GameProfileEntity, bindings: List<KeyBindingEntity>) {
        activeProfileId = profile.id
        activeProfileName = profile.name
        sensitivityHorizontal = profile.sensitivityHorizontal
        sensitivityVertical = profile.sensitivityVertical
        dpiMultiplier = profile.dpiMultiplier
        bindingsByKeyCode = bindings
            .filter { it.gestureType != GestureType.LOOK_TOGGLE.name }
            .associateBy { it.keyCode }
        cameraBinding = bindings.firstOrNull { it.gestureType == GestureType.LOOK_TOGGLE.name }
    }

    fun clear() {
        isMapperActive = false
        isCameraCaptureActive = false
        activeProfileId = null
        activeProfileName = ""
        bindingsByKeyCode = emptyMap()
        cameraBinding = null
    }
}
