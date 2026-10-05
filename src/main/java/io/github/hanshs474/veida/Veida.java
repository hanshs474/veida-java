package io.github.hanshs474.veida;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Generate images from Java with no API key and no account.
 *
 * <pre>{@code
 * Veida.Image img = Veida.create().generate("a paper boat at sunrise, soft light");
 * System.out.println(img.url());
 * }</pre>
 *
 * <p>Calls the anonymous tier of <a href="https://veida.ai">veida.ai</a>, which
 * meters its free allowance against a client id this class invents rather than
 * an account you register. Measured against the running service: 4 credits per
 * id, 4 per image, 30 per IP per day. Free output is 1K and watermarked.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class Veida {

    /** The service this class talks to. */
    public static final String BASE_URL = "https://veida.ai";
    /** The free text-to-image engine. */
    public static final String MODEL = "veida-image-v1";
    /** Credit balance a signed-out caller starts with. */
    public static final int ANON_GRANT = 4;
    /** What one image costs, so the grant pays for exactly one. */
    public static final int COST_TEXT_TO_IMAGE = 4;
    /** Credits one machine may spend per day regardless of client ids. */
    public static final int IP_DAILY_CEILING = 30;
    /** Aspect ratios the free engine accepts. */
    public static final List<String> ASPECT_RATIOS = List.of("1:1", "16:9", "9:16", "4:3", "3:4");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String baseUrl;
    private final Duration pollEvery;
    private final Duration timeout;
    private final HttpClient http;

    private Veida(Builder b) {
        this.baseUrl = b.baseUrl;
        this.pollEvery = b.pollEvery;
        this.timeout = b.timeout;
        this.http = b.http != null ? b.http
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    /** @return a client with every default */
    public static Veida create() {
        return builder().build();
    }

    /** @return a builder for a configured client */
    public static Builder builder() {
        return new Builder();
    }

    /** One finished picture. */
    public static final class Image {
        private final String url;
        private final boolean watermarked;

        Image(String url, boolean watermarked) {
            this.url = url;
            this.watermarked = watermarked;
        }

        /** @return a permanent, cacheable CDN link */
        public String url() {
            return url;
        }

        /** @return whether a mark was drawn on it; signing in on veida.ai removes it */
        public boolean watermarked() {
            return watermarked;
        }

        @Override
        public String toString() {
            return url + (watermarked ? " (watermarked)" : "");
        }
    }

    /** Configures a {@link Veida}. */
    public static final class Builder {
        private String baseUrl = BASE_URL;
        private Duration pollEvery = Duration.ofSeconds(4);
        private Duration timeout = Duration.ofMinutes(4);
        private HttpClient http;

        private Builder() {}

        /** @param url override for tests or a proxy */
        public Builder baseUrl(String url) { this.baseUrl = url; return this; }
        /** @param d how often the job is polled */
        public Builder pollEvery(Duration d) { this.pollEvery = d; return this; }
        /** @param d bounds the whole call */
        public Builder timeout(Duration d) { this.timeout = d; return this; }
        /** @param c a preconfigured client, e.g. with a proxy */
        public Builder httpClient(HttpClient c) { this.http = c; return this; }
        /** @return the client */
        public Veida build() { return new Veida(this); }
    }

    /** @return a fresh client id; the grant pays for one image, so ids are never reused */
    public static String anonId() {
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        StringBuilder s = new StringBuilder("java-");
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    /** Square image. @see #generate(String, String) */
    public Image generate(String prompt) throws VeidaException {
        return generate(prompt, "1:1");
    }

    /**
     * Turn a prompt into an image. Naming the light, the material and the
     * composition moves the result far more than adding adjectives.
     *
     * @param prompt what to draw
     * @param aspectRatio one of {@link #ASPECT_RATIOS}
     * @return the finished picture
     * @throws VeidaException with a {@link VeidaException.Reason} saying what to do next
     */
    public Image generate(String prompt, String aspectRatio) throws VeidaException {
        if (prompt == null || prompt.isBlank()) {
            throw new VeidaException(VeidaException.Reason.OTHER, "prompt is required");
        }
        if (!ASPECT_RATIOS.contains(aspectRatio)) {
            throw new VeidaException(VeidaException.Reason.OTHER,
                    "aspectRatio must be one of " + String.join(", ", ASPECT_RATIOS));
        }
        Instant deadline = Instant.now().plus(timeout);
        String id = anonId();
        String body = "{\"provider\":\"kie\",\"mediaType\":\"image\",\"model\":" + Json.quote(MODEL)
                + ",\"scene\":\"text-to-image\",\"prompt\":" + Json.quote(prompt)
                + ",\"options\":{\"aspect_ratio\":" + Json.quote(aspectRatio) + "}}";
        HttpRequest submit = HttpRequest.newBuilder(URI.create(baseUrl + "/api/ai/generate"))
                .timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("x-anon-id", id)
                .header("user-agent", "veida-java/0.1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        String task = parseSubmit(send(submit));

        URI query = URI.create(baseUrl + "/api/ai/anon-query?taskId="
                + URLEncoder.encode(task, StandardCharsets.UTF_8) + "&provider=kie&mediaType=image");
        while (Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(pollEvery.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new VeidaException(VeidaException.Reason.OTHER, "interrupted", e);
            }
            Map<String, Object> env;
            try {
                env = send(HttpRequest.newBuilder(query).timeout(Duration.ofSeconds(60))
                        .header("x-anon-id", id).header("user-agent", "veida-java/0.1.0").GET().build());
            } catch (VeidaException e) {
                continue; // a dropped connection mid-queue is not a failed generation
            }
            if (!Long.valueOf(0).equals(asLong(env.get("code")))) continue;
            Image img = parsePoll(env);
            if (img != null) return img;
        }
        throw new VeidaException(VeidaException.Reason.TIMEOUT, "still queued when the deadline passed");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> send(HttpRequest req) throws VeidaException {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            Object parsed = Json.parse(res.body());
            if (!(parsed instanceof Map)) throw new VeidaException(VeidaException.Reason.OTHER, "unexpected response");
            return (Map<String, Object>) parsed;
        } catch (IOException e) {
            throw new VeidaException(VeidaException.Reason.OTHER, "transport: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VeidaException(VeidaException.Reason.OTHER, "interrupted", e);
        } catch (RuntimeException e) {
            throw new VeidaException(VeidaException.Reason.OTHER, "unreadable response", e);
        }
    }

    private static Long asLong(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : null;
    }

    /** The quota wall answers HTTP 200 with code 0, so it is recognised by a field. */
    @SuppressWarnings("unchecked")
    static String parseSubmit(Map<String, Object> env) throws VeidaException {
        if (!Long.valueOf(0).equals(asLong(env.get("code")))) {
            throw new VeidaException(VeidaException.Reason.OTHER, String.valueOf(env.get("message")));
        }
        Map<String, Object> d = env.get("data") instanceof Map ? (Map<String, Object>) env.get("data") : Map.of();
        if (Boolean.TRUE.equals(d.get("wall"))) {
            String why = "anon_ip_daily".equals(d.get("reason"))
                    ? "this machine has used its " + IP_DAILY_CEILING + " free credits for today"
                    : "free allowance spent (" + d.get("reason") + ")";
            throw new VeidaException(VeidaException.Reason.QUOTA, why + "; sign in at https://veida.ai/pricing");
        }
        Object id = d.get("id");
        if (id == null || id.toString().isEmpty()) {
            throw new VeidaException(VeidaException.Reason.OTHER, "the service returned no task id");
        }
        return id.toString();
    }

    /** Status is not monotonic, so only a terminal failure ends the wait early. */
    @SuppressWarnings("unchecked")
    static Image parsePoll(Map<String, Object> env) throws VeidaException {
        Map<String, Object> d = env.get("data") instanceof Map ? (Map<String, Object>) env.get("data") : Map.of();
        Object images = d.get("images");
        if (images instanceof List && !((List<Object>) images).isEmpty()) {
            Object wm = d.get("watermarked");
            boolean marked = wm instanceof List && !((List<Object>) wm).isEmpty()
                    && Boolean.TRUE.equals(((List<Object>) wm).get(0));
            return new Image(((List<Object>) images).get(0).toString(), marked);
        }
        String status = String.valueOf(d.get("status")).toLowerCase();
        if (status.equals("failed") || status.equals("error")) {
            throw new VeidaException(VeidaException.Reason.REJECTED,
                    "the prompt was refused by the content filter; reword it");
        }
        return null;
    }
}
