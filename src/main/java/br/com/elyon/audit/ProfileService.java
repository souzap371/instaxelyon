package br.com.elyon.audit;

import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;

@Service
class ProfileService {
    private final ObjectMapper json;
    private final InstagramBrowserService browser;

    record CollectedProfile(String evidence, String screenshot, String profileImage, Long followers, Long following) {}

    ProfileService(ObjectMapper json, InstagramBrowserService browser) {
        this.json = json;
        this.browser = browser;
    }

    static String username(String input) {
        String u = input.trim().replaceFirst("^https?://(www[.])?instagram[.]com/", "").replaceFirst("^@", "")
                .replaceAll("/+$", "");
        if (!u.matches("[A-Za-z0-9._]{1,30}"))
            throw new IllegalArgumentException("Informe um @perfil ou URL de perfil válida.");
        return u;
    }

    CollectedProfile collect(String username) throws Exception {
        String token = System.getenv("META_ACCESS_TOKEN"), id = System.getenv("META_IG_USER_ID");
        var capture = browser.collect(username);
        String evidence = capture.evidence();
        if (token != null && !token.isBlank() && id != null && id.matches("[0-9]+"))
            evidence += "\nDados oficiais Meta: " + collectMeta(username, token, id);
        return new CollectedProfile(evidence, capture.screenshot(), capture.profileImage(), capture.followers(), capture.following());
    }

    private String collectMeta(String username, String token, String id) throws Exception {
        String fields = "business_discovery.username(" + username
                + "){username,name,biography,website,followers_count,media_count,profile_picture_url,media.limit(24){caption,like_count,comments_count,timestamp,media_type,permalink}}";
        String version = System.getenv().getOrDefault("META_GRAPH_VERSION", "v23.0");
        if (!version.matches("v[0-9]+[.][0-9]+"))
            throw new IllegalArgumentException("Versão Meta inválida");
        var uri = URI.create("https://graph.facebook.com/" + version + "/" + id + "?fields="
                + URLEncoder.encode(fields, java.nio.charset.StandardCharsets.UTF_8));
        var r = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
                .send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(40))
                        .header("Authorization", "Bearer " + token).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200)
            throw new IllegalArgumentException(
                    "A Meta não disponibilizou o perfil. Verifique permissões, tipo da conta e validade da conexão.");
        JsonNode data = json.readTree(r.body()).path("business_discovery");
        if (data.isMissingNode())
            throw new IllegalArgumentException("Perfil não retornado pela Meta.");
        return data.toString();
    }

}
