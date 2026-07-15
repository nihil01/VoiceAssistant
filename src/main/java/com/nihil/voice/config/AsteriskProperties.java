package com.nihil.voice.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("voice.asterisk")
public record AsteriskProperties(
    boolean enabled,
    @NotNull URI baseUrl,
    String username,
    String password,
    @NotBlank String app,
    @NotBlank String mediaFormat,
    @NotBlank String mediaDirection,
    @NotNull URI mediaWsBaseUrl,
    @NotNull Duration requestTimeout,
    @NotNull Duration noFrameTimeout,
    @Min(1) int inboundBufferFrames,
    @Min(1) int outboundBufferFrames
) {
    public URI eventsUri() {
        String scheme = "https".equalsIgnoreCase(baseUrl.getScheme()) ? "wss" : "ws";
        return URI.create(scheme + "://" + baseUrl.getAuthority() + "/ari/events?app=" + encode(app) + "&subscribeAll=false");
    }

    public URI mediaUri(String connectionId) {
        String base = mediaWsBaseUrl.toString().replaceAll("/+$", "");
        return URI.create(base + "/" + encode(connectionId));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
