package cl.duoc.digitalfix.rabbitadmin.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Configuracion centralizada del administrador (prefijo digitalfix.rabbit-admin). */
@Validated
@ConfigurationProperties(prefix = "digitalfix.rabbit-admin")
public record RabbitAdminProperties(
    @NotBlank String vhost,
    @NotEmpty List<String> managementUrls,
    @NotBlank String managementUsername,
    @NotBlank String managementPassword,
    @NotBlank String deadLetterExchange,
    @NotBlank String deadLetterSuffix,
    List<String> protectedPrefixes,
    @Min(1) @Max(500) int maxBatch
) {
    public RabbitAdminProperties {
        protectedPrefixes = protectedPrefixes == null ? List.of() : List.copyOf(protectedPrefixes);
    }

    public boolean isProtected(String name) {
        return protectedPrefixes.stream().anyMatch(name::startsWith);
    }
}
