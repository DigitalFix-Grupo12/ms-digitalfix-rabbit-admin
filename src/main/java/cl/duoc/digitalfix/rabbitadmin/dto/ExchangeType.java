package cl.duoc.digitalfix.rabbitadmin.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Tipos de exchange de RabbitMQ (se aceptan en minuscula o mayuscula). */
public enum ExchangeType {
    DIRECT, TOPIC, FANOUT, HEADERS;

    @JsonCreator
    public static ExchangeType from(String value) {
        if (value == null) return null;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean acceptsWildcards() {
        return this == TOPIC;
    }
}
