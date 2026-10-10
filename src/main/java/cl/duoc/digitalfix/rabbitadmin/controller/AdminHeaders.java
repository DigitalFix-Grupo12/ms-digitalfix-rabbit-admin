package cl.duoc.digitalfix.rabbitadmin.controller;

/** Header con la identidad del usuario que propaga el BFF (para el log de auditoria). */
final class AdminHeaders {
    static final String USER = "X-User-Name";
    static final String UNKNOWN = "desconocido";

    private AdminHeaders() {}
}
