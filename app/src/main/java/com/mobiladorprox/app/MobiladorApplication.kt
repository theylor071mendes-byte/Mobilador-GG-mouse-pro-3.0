package com.mobiladorprox.app

import android.app.Application
import com.mobiladorprox.app.data.db.AppDatabase
import com.mobiladorprox.app.data.repository.ProfileRepository
import com.mobiladorprox.app.settings.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Ponto central de montagem das dependências do app (sem framework de DI,
 * para manter o projeto simples e previsível de compilar). Perfis de
 * keymap são criados sob demanda, quando o usuário escolhe um jogo
 * instalado em "+ Adicionar jogo" — não há mais perfis pré-semeados.
 */
class MobiladorApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { ProfileRepository(database.profileDao()) }
    val preferences by lazy { AppPreferences(this) }
}
