package br.com.elyon.audit;

import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

@Service
class InstagramBrowserService {
    record Capture(String evidence, String screenshot, String profileImage, Long followers, Long following) {}

    private static final List<Pattern> FOLLOWER_PATTERNS = List.of(
            Pattern.compile("\\\"(?:edge_followed_by|follower_count|followers_count)\\\"\\s*:\\s*(?:\\{\\s*\\\"count\\\"\\s*:\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([0-9][0-9.,\\s]*\\s*[kKmM]?)\\s+(?:followers|seguidores)", Pattern.CASE_INSENSITIVE));
    private static final List<Pattern> FOLLOWING_PATTERNS = List.of(
            Pattern.compile("\\\"(?:edge_follow|following_count|follows_count)\\\"\\s*:\\s*(?:\\{\\s*\\\"count\\\"\\s*:\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([0-9][0-9.,\\s]*\\s*[kKmM]?)\\s+(?:following|seguindo)", Pattern.CASE_INSENSITIVE));

    Capture collect(String username) {
        ChromeOptions options = new ChromeOptions();
        options.setBinary(System.getenv().getOrDefault("CHROME_BIN", "/usr/bin/chromium"));
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu",
                "--window-size=1440,1400", "--lang=pt-BR",
                "--user-agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        String driverPath = System.getenv().getOrDefault("CHROMEDRIVER_PATH", "/usr/bin/chromedriver");
        System.setProperty("webdriver.chrome.driver", driverPath);
        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(50));
            String profileUrl = "https://www.instagram.com/" + username + "/";
            driver.get(profileUrl);
            new WebDriverWait(driver, Duration.ofSeconds(15)).until(d ->
                    ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete"));
            dismissCookies(driver);
            pause(2500);
            dismissLoginOverlay(driver);
            pause(800);

            String title = safe(driver.getTitle());
            String body = safe(driver.findElement(By.tagName("body")).getText());
            String source = safe(driver.getPageSource());
            String combined = (title + "\n" + body + "\n" + source).toLowerCase(Locale.ROOT);
            boolean exactProfile = combined.contains("@" + username.toLowerCase(Locale.ROOT))
                    || combined.contains("instagram.com/" + username.toLowerCase(Locale.ROOT))
                    || combined.contains("\"username\":\"" + username.toLowerCase(Locale.ROOT) + "\"");
            boolean blocked = combined.contains("confirm it's you") || combined.contains("challenge_required")
                    || combined.contains("rate_limit") || combined.contains("too many requests")
                    || body.contains("Sorry, something went wrong");
            if (blocked || !exactProfile)
                throw new IllegalArgumentException("O Instagram não liberou o perfil público para coleta automática agora. Aguarde alguns minutos e tente novamente.");

            List<String> meta = new ArrayList<>();
            for (WebElement element : driver.findElements(By.cssSelector("meta[name='description'],meta[property='og:description'],meta[property='og:title']"))) {
                String value = safe(element.getAttribute("content")).trim();
                if (!value.isBlank() && !meta.contains(value)) meta.add(value);
            }
            List<String> posts = new ArrayList<>();
            for (WebElement link : driver.findElements(By.cssSelector("a[href*='/p/'],a[href*='/reel/']"))) {
                String href = safe(link.getAttribute("href"));
                if (!href.isBlank() && !posts.contains(href) && posts.size() < 24) posts.add(href);
            }
            List<String> imageDescriptions = new ArrayList<>();
            String profileImage = null;
            for (WebElement image : driver.findElements(By.cssSelector("img[alt]"))) {
                String alt = safe(image.getAttribute("alt")).trim();
                if (!alt.isBlank() && !imageDescriptions.contains(alt) && imageDescriptions.size() < 24)
                    imageDescriptions.add(alt);
                String normalizedAlt = alt.toLowerCase(Locale.ROOT);
                if (profileImage == null && image.isDisplayed()
                        && (normalizedAlt.contains("profile picture") || normalizedAlt.contains("foto do perfil")
                                || normalizedAlt.contains(username.toLowerCase(Locale.ROOT)))) {
                    Rectangle rect = image.getRect();
                    if (rect.width >= 60 && rect.height >= 60 && Math.abs(rect.width - rect.height) < 20) {
                        try { profileImage = "data:image/png;base64," + image.getScreenshotAs(OutputType.BASE64); }
                        catch (Exception ignored) {}
                    }
                }
            }
            String screenshot = "data:image/png;base64," + ((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64);
            Long followers = findCount(title + "\n" + String.join(" | ", meta) + "\n" + body + "\n" + source,
                    FOLLOWER_PATTERNS);
            Long following = findCount(title + "\n" + String.join(" | ", meta) + "\n" + body + "\n" + source,
                    FOLLOWING_PATTERNS);
            String visible = body.length() > 18000 ? body.substring(0, 18000) : body;
            String evidence = "PERFIL EXATO CONFIRMADO PELO NAVEGADOR\nURL: " + profileUrl
                    + "\nSeguidores coletados: " + displayCount(followers)
                    + "\nSeguindo coletados: " + displayCount(following)
                    + "\nTítulo: " + title + "\nMetadados: " + String.join(" | ", meta)
                    + "\nTexto visível:\n" + visible + "\nPublicações visíveis: " + String.join(", ", posts)
                    + "\nDescrições das imagens: " + String.join(" | ", imageDescriptions);
            return new Capture(evidence, screenshot, profileImage, followers, following);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Não foi possível abrir o perfil no navegador automático. Tente novamente em alguns minutos.");
        } finally {
            if (driver != null) try { driver.quit(); } catch (Exception ignored) {}
        }
    }

    private void dismissCookies(WebDriver driver) {
        for (WebElement button : driver.findElements(By.tagName("button"))) {
            String text = safe(button.getText()).toLowerCase(Locale.ROOT);
            if (text.contains("allow all") || text.contains("permitir todos") || text.contains("aceitar todos")) {
                try { button.click(); pause(500); } catch (Exception ignored) {}
                return;
            }
        }
    }

    private void dismissLoginOverlay(WebDriver driver) {
        for (WebElement icon : driver.findElements(By.cssSelector(
                "svg[aria-label='Close'],svg[aria-label='Fechar'],[aria-label='Close'],[aria-label='Fechar']"))) {
            try {
                WebElement clickable = icon.findElement(By.xpath("ancestor-or-self::*[self::button or @role='button'][1]"));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
                pause(700);
                return;
            } catch (Exception ignored) {}
        }
        for (WebElement button : driver.findElements(By.cssSelector("button,[role='button']"))) {
            String label = (safe(button.getAttribute("aria-label")) + " " + safe(button.getText()))
                    .toLowerCase(Locale.ROOT);
            if (label.matches(".*(?:close|fechar|agora não|not now).*")) {
                try { button.click(); pause(500); return; } catch (Exception ignored) {}
            }
        }
        for (WebElement dialog : driver.findElements(By.cssSelector("[role='dialog']"))) {
            String text = safe(dialog.getText()).toLowerCase(Locale.ROOT);
            if (text.contains("see photos") || text.contains("log in") || text.contains("entrar")) {
                Rectangle box = dialog.getRect();
                for (WebElement clickable : dialog.findElements(By.cssSelector("button,[role='button']"))) {
                    try {
                        Rectangle target = clickable.getRect();
                        int centerX = target.x + target.width / 2;
                        int centerY = target.y + target.height / 2;
                        boolean topRight = centerX > box.x + (box.width * 2 / 3)
                                && centerY < box.y + (box.height / 3);
                        if (topRight) {
                            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
                            pause(700);
                            return;
                        }
                    } catch (Exception ignored) {}
                }
                try {
                    ((JavascriptExecutor) driver).executeScript(
                            "const d=arguments[0]; const p=d.parentElement; d.remove(); "
                            + "if(p && getComputedStyle(p).position==='fixed') p.remove(); "
                            + "document.body.style.overflow='auto';", dialog);
                } catch (Exception ignored) {}
            }
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }
    static Long findCount(String text, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(safe(text));
            if (matcher.find()) return parseCount(matcher.group(1));
        }
        return null;
    }

    static Long parseCount(String raw) {
        String value = raw.trim().replace(" ", "");
        double multiplier = 1;
        if (value.matches("(?i).*[km]$")) {
            char suffix = Character.toLowerCase(value.charAt(value.length() - 1));
            multiplier = suffix == 'k' ? 1_000 : 1_000_000;
            value = value.substring(0, value.length() - 1).replace(',', '.');
        } else {
            value = value.replace(".", "").replace(",", "");
        }
        try {
            return Math.round(Double.parseDouble(value) * multiplier);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String displayCount(Long value) { return value == null ? "não disponível" : value.toString(); }
    private static void pause(long millis) { try { Thread.sleep(millis); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } }
}
