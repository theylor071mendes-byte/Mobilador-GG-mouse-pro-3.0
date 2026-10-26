package com.mobiladorprox.app.data.model

/** Tipo de gesto disparado pelo Serviço de Acessibilidade para uma tecla. */
enum class GestureType(val displayName: String) {
    /** Toque rápido e único (ex.: pular, recarregar, abrir menu). */
    TAP("Toque único"),

    /** Mantém o toque enquanto a tecla estiver pressionada (ex.: mirar, mover). */
    HOLD("Pressionar e segurar"),

    /** Alterna a captura do mouse para controlar a câmera (visão/mira livre). */
    LOOK_TOGGLE("Alternar câmera (mouse)")
}

/**
 * Perfis de desempenho do app. Cada um ajusta apenas o que o aplicativo
 * realmente controla: a suavização aplicada ao movimento da câmera (mouse)
 * e os efeitos visuais da própria interface. Não há qualquer promessa de
 * eliminar tremor de mira — apenas reduzir o quanto é percebido, filtrando
 * o movimento bruto do ponteiro antes de virar gesto.
 */
enum class PerformanceProfile(
    val displayName: String,
    val description: String,
    /** 1f = sem suavização (resposta crua); valores menores suavizam mais o movimento da câmera. */
    val lookResponsiveness: Float,
    /** Quando verdadeiro, a interface do app reduz animações e efeitos próprios. */
    val reduceAppAnimations: Boolean
) {
    ALTO(
        "ALTO",
        "Prioriza qualidade e resposta: o movimento da câmera segue o mouse quase sem filtro.",
        lookResponsiveness = 1f,
        reduceAppAnimations = false
    ),
    MEDIO(
        "MÉDIO",
        "Equilíbrio entre resposta e estabilidade: leve suavização no movimento da câmera.",
        lookResponsiveness = 0.65f,
        reduceAppAnimations = false
    ),
    BAIXO(
        "BAIXO",
        "Prioriza leveza e consistência: suavização mais forte para reduzir tremor percebido.",
        lookResponsiveness = 0.4f,
        reduceAppAnimations = true
    ),
    MUITO_BAIXO(
        "MUITO BAIXO",
        "Mínimo de efeitos e processamento extra: máxima suavização e animações do app desligadas.",
        lookResponsiveness = 0.22f,
        reduceAppAnimations = true
    )
}

/** Opções de taxa de polling que o usuário PREFERE para o mouse (ver limitação em MouseSettingsScreen). */
enum class PollingRate(val label: String, val hz: Int?) {
    HZ_150("150 Hz", 150),
    HZ_250("250 Hz", 250),
    HZ_500("500 Hz", 500),
    UNLIMITED("Sem limite", null)
}
