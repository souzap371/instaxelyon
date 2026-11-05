package br.com.elyon.audit;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class InstagramBrowserServiceTest {
    @Test
    void parsesPlainAndAbbreviatedCounts() {
        assertEquals(1234L, InstagramBrowserService.parseCount("1,234"));
        assertEquals(12300L, InstagramBrowserService.parseCount("12.3K"));
        assertEquals(2500000L, InstagramBrowserService.parseCount("2,5M"));
        assertNull(InstagramBrowserService.parseCount("indisponível"));
    }
}
