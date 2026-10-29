package com.mobiladorprox.app.ui.inputtest

import android.view.KeyEvent

/**
 * Ponte simples entre a Activity (que recebe os KeyEvent brutos do teclado
 * físico via dispatchKeyEvent, de fora da árvore de Compose) e a tela de
 * teste de teclado/mouse, que só precisa ouvir enquanto estiver visível.
 * Retornar true no listener consome o evento (impede que uma tecla como W/A/S/D
 * mova o foco entre componentes do Compose durante o teste).
 */
object InputTestBridge {
    var listener: ((KeyEvent) -> Boolean)? = null
}
