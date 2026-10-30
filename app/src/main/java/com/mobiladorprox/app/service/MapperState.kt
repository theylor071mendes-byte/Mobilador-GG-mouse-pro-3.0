package com.mobiladorprox.app.service

import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import com.mobiladorprox.app.data.model.GestureType
import com.mobiladorprox.app.data.model.PollingRate

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

    /** Pacote do jogo selecionado (ex.: "com.dts.freefireth"), usado para encerrar o mapeador sozinho ao sair dele. */
    @Volatile
    var activeGamePackage: String? = null

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

    /** Preferência de taxa de resposta do mouse (apenas referência; ver limitação em AppPreferences). */
    @Volatile
    var pollingRate: PollingRate = PollingRate.UNLIMITED

    /** 1f = resposta crua; valores menores suavizam o movimento da câmera. Vem do Perfil de desempenho. */
    @Volatile
    var lookResponsiveness: Float = 0.65f

    /** Velocidade horizontal da câmera no controle livre ("Suspender mouse"), 0 a 1000; 500 = padrão (1x). */
    @Volatile
    var freeLookSpeedX: Float = 500f

    /** Velocidade vertical da puxada (olhar para cima/baixo) no controle livre, 0 a 1000; 500 = padrão (1x). */
    @Volatile
    var freeLookSpeedY: Float = 500f

    fun loadProfile(profile: GameProfileEntity, bindings: List<KeyBindingEntity>) {
        activeProfileId = profile.id
        activeProfileName = profile.profileName
        activeGamePackage = profile.packageName
        // ANALOG_STICK fica de fora: seu movimento vem das teclas WASD fixas
        // (tratadas à parte em onKeyEvent), não de uma tecla própria sua.
        // LOOK_TOGGLE PRECISA continuar aqui: é a tecla/botão configurável
        // (ex.: "M") que liga/desliga a Visualização de câmera — excluí-la
        // deixava esse botão sem nenhuma tecla vinculada de verdade.
        bindingsByKeyCode = bindings
            .filter { it.gestureType != GestureType.ANALOG_STICK.name }
            .associateBy { it.keyCode }
        cameraBinding = bindings.firstOrNull { it.gestureType == GestureType.LOOK_TOGGLE.name }
        analogStickBinding = bindings.firstOrNull { it.gestureType == GestureType.ANALOG_STICK.name }
    }

    fun clear() {
        isMapperActive = false
        isCameraCaptureActive = false
        activeProfileId = null
        activeProfileName = ""
        activeGamePackage = null
        bindingsByKeyCode = emptyMap()
        cameraBinding = null
        analogStickBinding = null
    }
}
