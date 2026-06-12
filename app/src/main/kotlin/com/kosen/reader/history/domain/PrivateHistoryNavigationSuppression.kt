package com.kosen.reader.history.domain

import java.util.concurrent.atomic.AtomicInteger

/**
 * Evita bloquear o histórico oculto ao abrir detalhes/leitor e voltar.
 * O bloqueio continua ao trocar de aba, sair do modo oculto ou minimizar o app.
 */
object PrivateHistoryNavigationSuppression {

	private val depth = AtomicInteger(0)

	val isActive: Boolean
		get() = depth.get() > 0

	fun begin() {
		depth.incrementAndGet()
	}

	fun end() {
		depth.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
	}
}
