package br.com.elyon.audit;

import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/trafego")
class TrafficController {
    private final MetaConnectionRepository connections;
    private final MetaGraphClient meta;
    private final MetaTokenCipher cipher;
    private final SecureRandom random = new SecureRandom();

    TrafficController(MetaConnectionRepository connections, MetaGraphClient meta, MetaTokenCipher cipher) {
        this.connections = connections;
        this.meta = meta;
        this.cipher = cipher;
    }

    @GetMapping
    String home(Model model) {
        model.addAttribute("connections", connections.findAll());
        model.addAttribute("configured", meta.configured() && cipher.configured());
        model.addAttribute("writeEnabled", meta.writeEnabled());
        model.addAttribute("graphVersion", meta.version());
        return "traffic";
    }

    @GetMapping("/conectar")
    String connect(@RequestParam String clientName, HttpSession session) {
        if (!meta.configured() || !cipher.configured())
            throw new IllegalArgumentException("Configure a integração Meta antes de conectar um cliente.");
        String name = clientName == null ? "" : clientName.trim();
        if (name.length() < 2 || name.length() > 120)
            throw new IllegalArgumentException("Informe o nome do cliente.");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute("metaOAuthState", state);
        session.setAttribute("metaClientName", name);
        return "redirect:" + meta.authorizationUri(state);
    }

    @GetMapping("/callback")
    String callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
            @RequestParam(required = false) String error, HttpSession session, RedirectAttributes flash) {
        try {
            if (error != null) throw new IllegalArgumentException("A autorização Meta foi cancelada ou recusada.");
            String expected = (String) session.getAttribute("metaOAuthState");
            String clientName = (String) session.getAttribute("metaClientName");
            session.removeAttribute("metaOAuthState");
            session.removeAttribute("metaClientName");
            if (expected == null || state == null || !java.security.MessageDigest.isEqual(
                    expected.getBytes(java.nio.charset.StandardCharsets.UTF_8), state.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                throw new IllegalArgumentException("A autorização Meta expirou ou é inválida.");
            if (code == null || code.isBlank()) throw new IllegalArgumentException("A Meta não retornou o código de autorização.");
            String token = meta.exchangeCode(code);
            var me = meta.me(token);
            MetaConnection connection = new MetaConnection();
            connection.clientName = clientName;
            connection.metaUserId = me.path("id").asText();
            connection.metaUserName = me.path("name").asText();
            connection.encryptedAccessToken = cipher.encrypt(token);
            connections.save(connection);
            flash.addFlashAttribute("success", "Conta Meta conectada com segurança.");
            return "redirect:/trafego/" + connection.id;
        } catch (Exception ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/trafego";
        }
    }

    @GetMapping("/{id}")
    String client(@PathVariable Long id, Model model) throws Exception {
        MetaConnection connection = connection(id);
        model.addAttribute("connection", connection);
        model.addAttribute("accounts", meta.accounts(cipher.decrypt(connection.encryptedAccessToken)));
        model.addAttribute("writeEnabled", meta.writeEnabled());
        return "traffic-client";
    }

    @GetMapping("/{id}/contas/{accountId}")
    String account(@PathVariable Long id, @PathVariable String accountId, Model model) throws Exception {
        MetaConnection connection = connection(id);
        String token = cipher.decrypt(connection.encryptedAccessToken);
        model.addAttribute("connection", connection);
        model.addAttribute("accountId", accountId);
        model.addAttribute("campaigns", meta.campaigns(accountId, token));
        model.addAttribute("insights", meta.insights(accountId, token));
        model.addAttribute("writeEnabled", meta.writeEnabled());
        return "traffic-account";
    }

    @PostMapping("/{id}/contas/{accountId}/campanhas")
    String createCampaign(@PathVariable Long id, @PathVariable String accountId, @RequestParam String name,
            @RequestParam String objective, @RequestParam(defaultValue = "false") boolean confirmed,
            RedirectAttributes flash) {
        try {
            if (!confirmed) throw new IllegalArgumentException("Confirme a criação da campanha pausada.");
            MetaConnection connection = connection(id);
            meta.createCampaign(accountId, name, objective, cipher.decrypt(connection.encryptedAccessToken));
            flash.addFlashAttribute("success", "Campanha criada como PAUSADA na Meta.");
        } catch (Exception ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/trafego/" + id + "/contas/" + accountId;
    }

    private MetaConnection connection(Long id) {
        return connections.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
