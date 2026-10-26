package com.mobiladorprox.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "key_bindings",
    foreignKeys = [
        ForeignKey(
            entity = GameProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class KeyBindingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    /** Identificador estável da ação (ex.: "fire", "jump", "move_up"). */
    val actionId: String,
    /** Rótulo legível exibido na interface (ex.: "Atirar"). */
    val label: String,
    /** Código de tecla do Android (KeyEvent.KEYCODE_*) vinculado a esta ação. */
    val keyCode: Int,
    val gestureType: String,
    /** Posição na tela em porcentagem (0f a 1f) da largura/altura. */
    val xPercent: Float,
    val yPercent: Float,
    /** Escala visual do botão (1f = tamanho padrão). Permite redimensionar no editor. */
    val sizePercent: Float = 1f,
    /** Usado apenas por ANALOG_STICK: 4 keyCodes separados por vírgula, na ordem cima,baixo,esquerda,direita. */
    val extraKeyCodes: String? = null
)
