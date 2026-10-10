package cl.duoc.digitalfix.rabbitadmin.dto;

/** Reglas de nombres compartidas por los DTO y los parametros de ruta. */
public final class RabbitNames {

    /** Cola/exchange: sin espacios, 1-200 caracteres, sin el prefijo reservado amq. */
    public static final String NAME_REGEX = "^(?!amq\\.)[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$";
    public static final String NAME_MESSAGE =
        "debe tener 1 a 200 caracteres (letras, numeros, '.', '_', ':' o '-'), sin espacios y sin el prefijo reservado 'amq.'";

    /** Routing key / patron de binding: admite comodines * y # (solo validos en exchanges topic). */
    public static final String BINDING_KEY_REGEX = "^[A-Za-z0-9._:*#-]{0,255}$";
    public static final String BINDING_KEY_MESSAGE =
        "debe tener hasta 255 caracteres (letras, numeros, '.', '_', ':', '-', '*' o '#')";

    /** Routing key al publicar: sin comodines. */
    public static final String PUBLISH_KEY_REGEX = "^[A-Za-z0-9._:-]{0,255}$";
    public static final String PUBLISH_KEY_MESSAGE =
        "debe tener hasta 255 caracteres (letras, numeros, '.', '_', ':' o '-'); los comodines no se usan al publicar";

    /** Exchange destino al publicar: "" (default) o un nombre existente, incluidos amq.*. */
    public static final String PUBLISH_EXCHANGE_REGEX = "^[A-Za-z0-9._:-]{0,200}$";

    private RabbitNames() {}
}
