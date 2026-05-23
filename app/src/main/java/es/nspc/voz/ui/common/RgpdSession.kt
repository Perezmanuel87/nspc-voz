package es.nspc.voz.ui.common

/**
 * Marca, in-memory para todo el proceso de la app, si el gestor ya ha
 * aceptado en esta sesión el recordatorio RGPD de grabación. Al matar la
 * app vuelve a false → mismo comportamiento que sessionStorage en el web.
 */
object RgpdSession {
    var aceptado: Boolean = false
}
