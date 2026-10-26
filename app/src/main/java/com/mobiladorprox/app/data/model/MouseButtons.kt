package com.mobiladorprox.app.data.model

/**
 * Códigos sintéticos usados para representar cliques do mouse como se
 * fossem "keyCode" de um vínculo (KeyBindingEntity.keyCode). Todos são
 * negativos para nunca colidir com um KeyEvent.KEYCODE_* real do Android
 * (que são sempre >= 0), permitindo reaproveitar o mesmo mapa
 * [com.mobiladorprox.app.service.MapperState.bindingsByKeyCode] tanto para
 * teclas do teclado quanto para cliques do mouse.
 */
object MouseButtons {
    const val LEFT = -101
    const val RIGHT = -102
    const val MIDDLE = -103

    fun isMouseButton(code: Int): Boolean = code == LEFT || code == RIGHT || code == MIDDLE

    fun labelFor(code: Int): String? = when (code) {
        LEFT -> "Clique esquerdo"
        RIGHT -> "Clique direito"
        MIDDLE -> "Clique do meio"
        else -> null
    }
}
