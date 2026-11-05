package br.com.elyon.audit;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
class MetaGraphClient {
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    MetaGraphClient(ObjectMapper json) { this.json = json; }

    boolean configured() {
        return present("META_APP_ID") && present("META_APP_SECRET") && present("META_REDIRECT_URI");
    }
    boolean writeEnabled() { return "true".equalsIgnoreCase(System.getenv("META_WRITE_ENABLED")); }
    String version() { return System.getenv().getOrDefault("META_GRAPH_VERSION", "v26.0"); }
    String redirectUri() { return required("META_REDIRECT_URI"); }

    URI authorizationUri(String state) {
        String params = "client_id=" + enc(required("META_APP_ID")) + "&redirect_uri=" + enc(redirectUri())
                + "&state=" + enc(state) + "&response_type=code&scope="
                + enc("ads_read,ads_management,business_management");
        return URI.create("https://www.facebook.com/" + version() + "/dialog/oauth?" + params);
    }

    String exchangeCode(String code) throws Exception {
        String query = "client_id=" + enc(required("META_APP_ID")) + "&client_secret=" + enc(required("META_APP_SECRET"))
                + "&redirect_uri=" + enc(redirectUri()) + "&code=" + enc(code);
        JsonNode shortToken = request("GET", "/oauth/access_token?" + query, null, null);
        String token = shortToken.path("access_token").asText();
        if (token.isBlank()) throw new IllegalArgumentException("A Meta não retornou um token de acesso.");
        try {
            String longQuery = "grant_type=fb_exchange_token&client_id=" + enc(required("META_APP_ID"))
                    + "&client_secret=" + enc(required("META_APP_SECRET")) + "&fb_exchange_token=" + enc(token);
            String longToken = request("GET", "/oauth/access_token?" + longQuery, null, null)
                    .path("access_token").asText();
            if (!longToken.isBlank()) token = longToken;
        } catch (Exception ignored) { /* conserva o token válido de curta duração */ }
        return token;
    }

    JsonNode me(String token) throws Exception { return get("/me", "fields=id,name", token); }
    JsonNode accounts(String token) throws Exception {
        return get("/me/adaccounts", "fields=id,name,account_status,currency,timezone_name,amount_spent&limit=100", token).path("data");
    }
    JsonNode campaigns(String accountId, String token) throws Exception {
        validateAccount(accountId);
        return get("/" + accountId + "/campaigns", "fields=id,name,status,effective_status,objective,daily_budget,lifetime_budget,start_time,stop_time,updated_time&limit=100", token).path("data");
    }
    JsonNode insights(String accountId, String token) throws Exception {
        validateAccount(accountId);
        return get("/" + accountId + "/insights", "fields=campaign_id,campaign_name,spend,impressions,reach,clicks,ctr,cpc,cpm,frequency,actions&level=campaign&date_preset=last_30d&limit=100", token).path("data");
    }
    JsonNode createCampaign(String accountId, String name, String objective, String token) throws Exception {
        validateAccount(accountId);
        if (!writeEnabled()) throw new IllegalArgumentException("A criação de campanhas está desabilitada no servidor.");
        if (name == null || name.isBlank() || name.length() > 200) throw new IllegalArgumentException("Nome de campanha inválido.");
        Set<String> allowed = Set.of("OUTCOME_AWARENESS", "OUTCOME_TRAFFIC", "OUTCOME_ENGAGEMENT", "OUTCOME_LEADS", "OUTCOME_SALES", "OUTCOME_APP_PROMOTION");
        if (!allowed.contains(objective)) throw new IllegalArgumentException("Objetivo de campanha inválido.");
        String body = "name=" + enc(name.trim()) + "&objective=" + enc(objective)
                + "&status=PAUSED&special_ad_categories=" + enc("[]")
                + "&access_token=" + enc(token) + "&appsecret_proof=" + enc(proof(token));
        return request("POST", "/" + accountId + "/campaigns", body, token);
    }

    private JsonNode get(String path, String query, String token) throws Exception {
        String suffix = query + "&access_token=" + enc(token) + "&appsecret_proof=" + enc(proof(token));
        return request("GET", path + "?" + suffix, null, token);
    }
    private JsonNode request(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/" + version() + path))
                .timeout(Duration.ofSeconds(50)).header("Accept", "application/json");
        if ("POST".equals(method)) builder.header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)); else builder.GET();
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode parsed = json.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = parsed.path("error").path("message").asText("Falha na comunicação com a Meta.");
            throw new IllegalArgumentException("Meta: " + message);
        }
        return parsed;
    }
    private String proof(String token) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(required("META_APP_SECRET").getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
    }
    private static void validateAccount(String id) {
        if (id == null || !id.matches("act_[0-9]+")) throw new IllegalArgumentException("Conta de anúncios inválida.");
    }
    private static boolean present(String name) { String value = System.getenv(name); return value != null && !value.isBlank(); }
    private static String required(String name) { String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException(name + " não configurado."); return value; }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
