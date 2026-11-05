package br.com.elyon.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void parsesPlainAndMarkdownJson() throws Exception {
        assertTrue(AiService.parseReport(json, "{\"ok\":true}").path("ok").asBoolean());
        assertTrue(AiService.parseReport(json, "```json\n{\"ok\":true}\n```").path("ok").asBoolean());
    }

    @Test
    void rejectsNonJsonOutput() {
        assertThrows(IllegalArgumentException.class, () -> AiService.parseReport(json, "sem json"));
    }
}
