package com.mobiladorprox.app.data.model

/** Categoria do perfil de mapeamento. */
enum class GameType(val displayName: String) {
    FREE_FIRE("Free Fire"),
    OUTRO_JOGO("Outro jogo"),
    PERSONALIZADO("Personalizado")
}

/**
 * Predefinições de sensibilidade. Cada uma aplica um conjunto de valores
 * numéricos (sensibilidade, velocidade de cursor, multiplicador de "DPI")
 * pensado para um estilo de jogo diferente. Não há qualquer garantia de
 * desempenho associada — são apenas combinações de configuração.
 */
enum class PresetMode(val displayName: String) {
    PRECISAO("Precisão"),
    RUSH("Rush"),
    EQUILIBRIO("Equilíbrio"),
    LONGA_DISTANCIA("Longa distância"),
    PERSONALIZADO("Personalizado")
}

/** Tipo de gesto disparado pelo Serviço de Acessibilidade para uma tecla. */
enum class GestureType(val displayName: String) {
    /** Toque rápido e único (ex.: pular, recarregar, abrir menu). */
    TAP("Toque único"),

    /** Mantém o toque enquanto a tecla estiver pressionada (ex.: mirar, mover). */
    HOLD("Pressionar e segurar"),

    /** Alterna a captura do mouse para controlar a câmera (visão/mira livre). */
    LOOK_TOGGLE("Alternar câmera (mouse)")
}
