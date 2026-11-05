package br.com.elyon.audit;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;

@Controller
class AuditController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuditController.class);
    private final AuditRepository repo;
    private final ProfileService profiles;
    private final AiService ai;
    private final ObjectMapper json;

    AuditController(AuditRepository repo, ProfileService profiles, AiService ai, ObjectMapper json) {
        this.repo = repo;
        this.profiles = profiles;
        this.ai = ai;
        this.json = json;
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/")
    String home(@RequestParam(defaultValue = "0") int page, Model m) {
        int safePage = Math.max(0, page);
        var audits = repo.findAllByOrderByCreatedDesc(PageRequest.of(safePage, 8));
        m.addAttribute("audits", audits.getContent());
        m.addAttribute("auditPage", audits);
        m.addAttribute("metaReady",
                System.getenv("META_ACCESS_TOKEN") != null && System.getenv("META_IG_USER_ID") != null);
        return "index";
    }

    @PostMapping("/analisar")
    String analyze(@RequestParam String username, @RequestParam(defaultValue = "") String segment,
            @RequestParam(defaultValue = "") String goal,
            @RequestParam(defaultValue = "automatic") String mode, @RequestParam(defaultValue = "") String evidence,
            @RequestParam(required = false) MultipartFile[] images, RedirectAttributes flash) {
        Audit a = new Audit();
        try {
            a.username = ProfileService.username(username);
            if (segment.length() > 200 || goal.length() > 1000 || evidence.length() > 25000)
                throw new IllegalArgumentException("Texto excede o limite permitido.");
            List<String> encoded = new ArrayList<>();
            if (images != null)
                for (var file : images) {
                    if (file.isEmpty())
                        continue;
                    if (encoded.size() >= 3)
                        throw new IllegalArgumentException("Envie no máximo três imagens.");
                    byte[] bytes = file.getBytes();
                    if (bytes.length > 5 * 1024 * 1024)
                        throw new IllegalArgumentException("Cada imagem deve ter até 5 MB.");
                    var im = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (im == null || (long) im.getWidth() * im.getHeight() > 24000000)
                        throw new IllegalArgumentException("Imagem inválida ou muito grande.");
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    ImageIO.write(im, "png", output);
                    encoded.add("data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray()));
                }
            if (!List.of("automatic", "meta", "manual").contains(mode))
                throw new IllegalArgumentException("Modo inválido.");
            ProfileService.CollectedProfile profile = !"manual".equals(mode) ? profiles.collect(a.username) : null;
            String collected = profile == null ? "" : profile.evidence();
            if (profile != null && profile.screenshot() != null) encoded.add(0, profile.screenshot());
            if (profile != null) {
                a.profileImage = profile.profileImage();
                a.followerCount = profile.followers();
                a.followingCount = profile.following();
            }
            if (collected.isBlank() && evidence.isBlank() && encoded.isEmpty())
                throw new IllegalArgumentException(
                        "Envie uma captura ou preencha as evidências para analisar. O @ sozinho exige integração Meta.");
            a.source = "manual".equals(mode) ? "Dados informados manualmente"
                    : "Coleta automática do perfil público";
            a.evidence = collected + "\nComplementos informados: " + evidence;
            a.screenshot = encoded.isEmpty() ? null : encoded.get(0);
            a.status = "PROCESSANDO";
            repo.save(a);
            a.report = json.writeValueAsString(ai.analyze(a.username, segment, goal, a.evidence, encoded));
            a.status = "CONCLUÍDO";
            repo.save(a);
            return "redirect:/analises/" + a.id;
        } catch (Exception ex) {
            log.error("Falha ao analisar o perfil {}: {}", a.username == null ? "inválido" : a.username,
                    ex.getMessage(), ex);
            if (a.id != null) {
                a.status = "FALHOU";
                a.error = ex instanceof IllegalArgumentException ? ex.getMessage()
                        : "Não foi possível concluir. Tente novamente.";
                repo.save(a);
            }
            flash.addFlashAttribute("error", ex instanceof IllegalArgumentException ? ex.getMessage()
                    : "Não foi possível concluir a análise. Verifique a conexão e tente novamente.");
            return "redirect:/";
        }
    }

    @GetMapping("/analises/{id}")
    String detail(@PathVariable Long id, Model m) throws Exception {
        Audit a = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        m.addAttribute("audit", a);
        m.addAttribute("r", a.report == null ? null : json.readTree(a.report));
        return "detail";
    }
}
