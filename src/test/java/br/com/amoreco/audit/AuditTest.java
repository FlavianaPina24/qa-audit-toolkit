package br.com.amoreco.audit;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuditTest extends BaseTest {

    private final String targetUrl = System.getProperty("targetUrl", "https://flavianapina.com.br");

    @BeforeEach
    public void navegarParaAuditoria(TestInfo testInfo) {
        super.configurarTeste(testInfo);
        getRelatorioTeste().info("Iniciando auditoria na URL: " + targetUrl);
        getPage().navigate(targetUrl);
    }

    @Test
    @DisplayName("CENÁRIO 1: Auditoria Completa de Acessibilidade (A11y) com Axe-Core")
    public void auditarAcessibilidadeAxe() {
        String tagsProperty = System.getProperty("axe.tags", "wcag2a,wcag2aa,wcag21a,wcag21aa");
        List<String> tags = Arrays.stream(tagsProperty.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        AxeBuilder axeBuilder = new AxeBuilder(getPage()).withTags(tags);

        String disabledRules = System.getProperty("axe.disabled.rules", "button-name");
        if (!disabledRules.isBlank()) {
            List<String> rulesToDisable = Arrays.stream(disabledRules.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            axeBuilder.disableRules(rulesToDisable);
        }

        AxeResults results = axeBuilder.analyze();
        List<Rule> violations = results.getViolations();

        long criticalCount = violations.stream().filter(v -> "critical".equalsIgnoreCase(v.getImpact())).count();
        long seriousCount = violations.stream().filter(v -> "serious".equalsIgnoreCase(v.getImpact())).count();
        long moderateCount = violations.stream().filter(v -> "moderate".equalsIgnoreCase(v.getImpact())).count();
        long minorCount = violations.stream().filter(v -> "minor".equalsIgnoreCase(v.getImpact())).count();

        getRelatorioTeste().info(String.format(
                "Axe-Core executado com tags %s. Violações encontradas: %d (Críticas: %d, Séria(s): %d, Moderadas: %d, Menores: %d)",
                tags, violations.size(), criticalCount, seriousCount, moderateCount, minorCount
        ));

        if (!violations.isEmpty()) {
            String htmlReport = formatarViolacoesAxe(violations);
            getRelatorioTeste().warning(htmlReport);
            registrarEvidencia("Violações de Acessibilidade Detectadas",
                    getPage().screenshot(new Page.ScreenshotOptions().setFullPage(true)));
        } else {
            getRelatorioTeste().pass("Nenhuma violação de acessibilidade encontrada pelas regras " + tags + "!");
        }

        boolean strictMode = Boolean.parseBoolean(System.getProperty("axe.strict", "false"));
        if (strictMode) {
            assertTrue(violations.isEmpty(),
                    "Foram encontradas " + violations.size() + " violações de acessibilidade. Verifique o relatório para detalhes.");
        } else {
            long blockingViolations = criticalCount + seriousCount;
            assertTrue(blockingViolations == 0,
                    String.format("Foram encontradas %d violações críticas e %d sérias de acessibilidade. Verifique o relatório para detalhes.",
                            criticalCount, seriousCount));
        }
    }

    @Test
    @DisplayName("CENÁRIO 1B: Verificação semântica básica de acessibilidade")
    public void verificarAcessibilidadeBasica() {
        String pageLanguage = getPage().getAttribute("html", "lang");
        getRelatorioTeste().info("Idioma da página detectado: " + pageLanguage);

        boolean hasMain = getPage().locator("main").count() > 0;
        if (!hasMain) {
            registrarEvidencia("Falta de elemento <main>",
                    getPage().screenshot(new Page.ScreenshotOptions().setFullPage(true)));
        }

        assertTrue(pageLanguage != null && !pageLanguage.isBlank(),
                "O elemento <html> deve conter o atributo lang para acessibilidade.");
        assertTrue(hasMain,
                "A página deve ter um elemento <main> para uma estrutura semântica adequada.");
    }
    @Test
    @DisplayName("CENÁRIO 2: Auditoria de Regressão Visual")
    public void auditarConsistenciaVisual() {
        String baselineFileName = "auditoria-visual-principal.png";
        boolean updateSnapshots = Boolean.parseBoolean(System.getProperty("update.snapshots", "false"));


        Path baselinePath = Path.of(BASELINE_DIR, baselineFileName);
        Path baselineDirPath = baselinePath.getParent();

        try {
            if (baselineDirPath != null) {
                Files.createDirectories(baselineDirPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível criar o diretório de baseline: " + e.getMessage(), e);
        }

        byte[] atualScreenshot = getPage().screenshot(new Page.ScreenshotOptions().setFullPage(true));

        if (updateSnapshots || !Files.exists(baselinePath)) {
            try {
                Files.write(baselinePath, atualScreenshot);
                getRelatorioTeste().info("Snapshot de referência '" + baselineFileName + "' foi criado/atualizado.");
            } catch (IOException e) {
                throw new RuntimeException("Falha ao gravar snapshot de baseline: " + e.getMessage(), e);
            }
            return;
        }

        byte[] expectedScreenshot;
        try {
            expectedScreenshot = Files.readAllBytes(baselinePath);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao ler o snapshot de baseline: " + e.getMessage(), e);
        }

        // Comparação por similaridade de pixels (tolerância padrão: 8% para acomodar renderização entre SOs)
        double threshold = Double.parseDouble(System.getProperty("visual.threshold", "0.08"));
        double diffFraction;
        try {
            diffFraction = compareAndWriteDiff(expectedScreenshot, atualScreenshot, baselineFileName);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao comparar imagens: " + e.getMessage(), e);
        }

        boolean dentroDaTolerancia = diffFraction <= threshold;
        if (!dentroDaTolerancia) {
            registrarEvidencia("Regressão visual detectada (diferença: " + String.format("%.2f%%", diffFraction * 100) + ")", atualScreenshot);
        }

        assertTrue(dentroDaTolerancia,
                "A página não corresponde ao snapshot de baseline: " + baselineFileName + ". Diferença: " + String.format("%.2f%%", diffFraction * 100));

        if (updateSnapshots) {
            getRelatorioTeste().info("Snapshot de referência '" + baselineFileName + "' foi criado/atualizado.");
        }
    }

    private double compareAndWriteDiff(byte[] expectedBytes, byte[] actualBytes, String baselineFileName) throws IOException {
        BufferedImage expected = ImageIO.read(new ByteArrayInputStream(expectedBytes));
        BufferedImage actual = ImageIO.read(new ByteArrayInputStream(actualBytes));

        if (expected == null || actual == null) {
            throw new IOException("Uma das imagens é inválida ou não pôde ser decodificada.");
        }

        int width = Math.max(expected.getWidth(), actual.getWidth());
        int height = Math.max(expected.getHeight(), actual.getHeight());

        BufferedImage diffImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        long diffPixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgbExpected = (x < expected.getWidth() && y < expected.getHeight()) ? expected.getRGB(x, y) : Color.WHITE.getRGB();
                int rgbActual = (x < actual.getWidth() && y < actual.getHeight()) ? actual.getRGB(x, y) : Color.WHITE.getRGB();

                if (rgbExpected != rgbActual) {
                    diffPixels++;
                    // highlight difference in red with some transparency
                    diffImage.setRGB(x, y, new Color(255, 0, 0, 180).getRGB());
                } else {
                    // copy expected pixel (desaturate to make diffs stand out)
                    int rgb = rgbExpected;
                    diffImage.setRGB(x, y, rgb);
                }
            }
        }

        double fraction = (double) diffPixels / (width * height);

        // salvar imagem de diff em screenshots
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(diffImage, "png", baos);
        byte[] diffBytes = baos.toByteArray();
        String diffFileName = getCurrentTestName() + "_diff_" + baselineFileName;
        registrarEvidencia("Diff visual: " + diffFileName, diffBytes);

        return fraction;
    }

    @Test
    @DisplayName("CENÁRIO 4: Verificação de imagens com texto alternativo")
    public void verificarImagensAlt() {
        int imagensSemAlt = getPage().locator("img:not([alt]), img[alt='']").count();
        if (imagensSemAlt > 0) {
            registrarEvidencia("Imagens sem atributo alt ou com alt vazio",
                    getPage().screenshot(new Page.ScreenshotOptions().setFullPage(true)));
        }

        assertTrue(imagensSemAlt == 0,
                "Todas as imagens devem ter atributo alt para acessibilidade.");
    }

    @Test
    @DisplayName("CENÁRIO 5: Verificação de links básicos")
    public void verificarLinksValidos() {
        int linksInvalidos = getPage().locator("a:not([href]), a[href=''], a[href^='javascript:']").count();
        if (linksInvalidos > 0) {
            registrarEvidencia("Links inválidos ou sem href detectados",
                    getPage().screenshot(new Page.ScreenshotOptions().setFullPage(true)));
        }

        assertTrue(linksInvalidos == 0,
                "Todos os links devem ter href válidos e não usar javascript:.");
    }

    @Test
    @DisplayName("CENÁRIO 3: Verificação básica da página inicial")
    public void verificarPaginaInicial() {
        String pageTitle = getPage().title();
        getRelatorioTeste().info("Título da página carregada: " + pageTitle);

        assertTrue(pageTitle != null && !pageTitle.isBlank(),
                "O título da página não pode estar vazio.");

        int headingCount = getPage().locator("main h1, main h2, header h1, header h2").count();
        if (headingCount == 0) {
            registrarEvidencia("Página inicial sem título detectado",
                    getPage().screenshot(new Page.ScreenshotOptions().setFullPage(true)));
        }
        assertTrue(headingCount > 0,
                "A página inicial deve conter pelo menos um título dentro de main ou header.");
    }

    @Test
    @DisplayName("CENÁRIO 6: Auditoria de SEO Técnico")
    public void auditarSeoTecnico() {
        StringBuilder seoReport = new StringBuilder();
        seoReport.append("<div style='font-family: Arial, sans-serif;'>");
        seoReport.append("<h4>🔍 Relatório de Auditoria de SEO Técnico</h4>");
        seoReport.append("<table style='width:100%; border-collapse: collapse; margin-top: 10px;'>");
        seoReport.append("<tr style='background: #f2f2f2; text-align: left;'>")
                .append("<th style='padding: 8px; border: 1px solid #ddd;'>Item</th>")
                .append("<th style='padding: 8px; border: 1px solid #ddd;'>Status</th>")
                .append("<th style='padding: 8px; border: 1px solid #ddd;'>Detalhes</th></tr>");

        int falhasSeo = 0;

        // 1. Title
        String title = getPage().title();
        boolean titleOk = title != null && !title.isBlank();
        int titleLength = titleOk ? title.length() : 0;
        boolean titleLengthIdeal = titleLength >= 30 && titleLength <= 65;
        adicionarLinhaTabela(seoReport, "Tag &lt;title&gt;", titleOk ? (titleLengthIdeal ? "✅ OK" : "⚠️ Atenção") : "❌ Falha",
                titleOk ? "Conteúdo: \"" + escapeHtml(title) + "\" (" + titleLength + " caracteres - Ideal: 30-65)" : "Não encontrada");
        if (!titleOk) falhasSeo++;

        // 2. Meta Description
        String metaDesc = getAttributeSafely("meta[name='description']", "content");
        boolean descOk = metaDesc != null && !metaDesc.isBlank();
        int descLength = descOk ? metaDesc.length() : 0;
        boolean descLengthIdeal = descLength >= 70 && descLength <= 160;
        adicionarLinhaTabela(seoReport, "Meta Description", descOk ? (descLengthIdeal ? "✅ OK" : "⚠️ Atenção") : "❌ Falha",
                descOk ? "Conteúdo: \"" + escapeHtml(metaDesc) + "\" (" + descLength + " caracteres - Ideal: 70-160)" : "Não encontrada");
        if (!descOk) falhasSeo++;

        // 3. Canonical Tag
        String canonical = getAttributeSafely("link[rel='canonical']", "href");
        boolean canonicalOk = canonical != null && !canonical.isBlank();
        adicionarLinhaTabela(seoReport, "Link Canonical", canonicalOk ? "✅ OK" : "⚠️ Ausente",
                canonicalOk ? "URL: " + escapeHtml(canonical) : "Recomendado para evitar conteúdo duplicado");

        // 4. Meta Robots
        String robots = getAttributeSafely("meta[name='robots']", "content");
        adicionarLinhaTabela(seoReport, "Meta Robots", (robots != null) ? "ℹ️ Definido" : "ℹ️ Padrão",
                (robots != null) ? "Diretivas: " + escapeHtml(robots) : "Sem tag específica (padrão dos buscadores é indexar)");

        // 5. Open Graph (Redes Sociais)
        String ogTitle = getAttributeSafely("meta[property='og:title']", "content");
        String ogImage = getAttributeSafely("meta[property='og:image']", "content");
        boolean ogOk = ogTitle != null && ogImage != null;
        adicionarLinhaTabela(seoReport, "Open Graph (og:title / og:image)", ogOk ? "✅ Presente" : "⚠️ Incompleto",
                "og:title: " + (ogTitle != null ? escapeHtml(ogTitle) : "Ausente") + " | og:image: " + (ogImage != null ? "Presente" : "Ausente"));

        // 6. Hierarquia de Títulos (H1, H2, H3)
        int h1Count = getPage().locator("h1").count();
        int h2Count = getPage().locator("h2").count();
        int h3Count = getPage().locator("h3").count();
        boolean h1Ok = h1Count == 1;
        adicionarLinhaTabela(seoReport, "Hierarquia de Headings", h1Ok ? "✅ Ideal" : (h1Count == 0 ? "❌ Crítico" : "⚠️ Múltiplos H1"),
                "H1: " + h1Count + " (ideal: 1), H2: " + h2Count + ", H3: " + h3Count);
        if (h1Count == 0) falhasSeo++;

        // 7. Robots.txt e Sitemap.xml
        String baseUrl = targetUrl.replaceAll("/+$", "");
        try {
            APIResponse robotsResp = getPage().request().get(baseUrl + "/robots.txt");
            adicionarLinhaTabela(seoReport, "Arquivo robots.txt", robotsResp.status() == 200 ? "✅ Encontrado" : "⚠️ HTTP " + robotsResp.status(),
                    "URL: " + baseUrl + "/robots.txt");
        } catch (Exception e) {
            adicionarLinhaTabela(seoReport, "Arquivo robots.txt", "⚠️ Erro", e.getMessage());
        }

        try {
            APIResponse sitemapResp = getPage().request().get(baseUrl + "/sitemap.xml");
            adicionarLinhaTabela(seoReport, "Arquivo sitemap.xml", sitemapResp.status() == 200 ? "✅ Encontrado" : "⚠️ HTTP " + sitemapResp.status(),
                    "URL: " + baseUrl + "/sitemap.xml");
        } catch (Exception e) {
            adicionarLinhaTabela(seoReport, "Arquivo sitemap.xml", "⚠️ Erro", e.getMessage());
        }

        seoReport.append("</table></div>");
        getRelatorioTeste().info(seoReport.toString());

        assertTrue(falhasSeo == 0, "Foram encontradas " + falhasSeo + " falhas críticas de SEO (Title ausente, Meta Description ausente ou ausência de H1).");
    }

    @Test
    @DisplayName("CENÁRIO 7: Auditoria de Segurança Web & Cabeçalhos HTTP")
    public void auditarSegurancaBasica() {
        StringBuilder secReport = new StringBuilder();
        secReport.append("<div style='font-family: Arial, sans-serif;'>");
        secReport.append("<h4>🔒 Relatório de Auditoria de Segurança</h4>");

        // 1. Cabeçalhos HTTP de Segurança
        APIResponse response = getPage().request().get(targetUrl);
        Map<String, String> headers = response.headers();

        secReport.append("<h5>Cabeçalhos de Segurança HTTP Detectados:</h5>");
        secReport.append("<table style='width:100%; border-collapse: collapse;'>");
        secReport.append("<tr style='background: #f2f2f2; text-align: left;'>")
                .append("<th style='padding: 8px; border: 1px solid #ddd;'>Cabeçalho</th>")
                .append("<th style='padding: 8px; border: 1px solid #ddd;'>Status</th>")
                .append("<th style='padding: 8px; border: 1px solid #ddd;'>Valor</th></tr>");

        String[] securityHeaders = {
                "strict-transport-security",
                "x-content-type-options",
                "x-frame-options",
                "content-security-policy",
                "referrer-policy",
                "permissions-policy"
        };

        for (String secHeader : securityHeaders) {
            String val = headers.get(secHeader);
            boolean present = val != null && !val.isBlank();
            adicionarLinhaTabela(secReport, "<code>" + secHeader + "</code>", present ? "✅ Presente" : "⚠️ Ausente",
                    present ? "<small>" + escapeHtml(val) + "</small>" : "Não configurado no servidor");
        }
        secReport.append("</table>");

        // 2. Links externos inseguros (target="_blank" sem rel="noopener" ou "noreferrer")
        int unsafeLinks = getPage().locator("a[target='_blank']:not([rel*='noopener']):not([rel*='noreferrer'])").count();
        if (unsafeLinks > 0) {
            secReport.append("<p style='color: #d9534f; margin-top: 10px;'><strong>⚠️ Vulnerabilidade Tabnabbing:</strong> ")
                    .append(unsafeLinks).append(" links com target='_blank' sem rel='noopener' ou 'noreferrer' detectados.</p>");
            registrarEvidencia("Links externos sem noopener/noreferrer",
                    getPage().screenshot(new Page.ScreenshotOptions().setFullPage(true)));
        } else {
            secReport.append("<p style='color: green; margin-top: 10px;'>✅ Todos os links externos utilizam rel='noopener' ou 'noreferrer' (Proteção contra Reverse Tabnabbing).</p>");
        }

        // 3. Verificação de Conteúdo Misto (HTTP em HTTPS) se a página for HTTPS
        boolean isHttps = targetUrl.startsWith("https://");
        int mixedContentCount = 0;
        if (isHttps) {
            mixedContentCount = getPage().locator("img[src^='http://'], script[src^='http://'], link[href^='http://'], iframe[src^='http://']").count();
            if (mixedContentCount > 0) {
                secReport.append("<p style='color: #d9534f;'><strong>🚨 Mixed Content Detectado:</strong> ")
                        .append(mixedContentCount).append(" recursos inseguros (HTTP) carregados em página HTTPS.</p>");
                registrarEvidencia("Mixed Content detectado",
                        getPage().screenshot(new Page.ScreenshotOptions().setFullPage(true)));
            } else {
                secReport.append("<p style='color: green;'>✅ Nenhum recurso de conteúdo misto (Mixed Content) detectado.</p>");
            }
        }

        secReport.append("</div>");
        getRelatorioTeste().info(secReport.toString());

        assertTrue(mixedContentCount == 0, "A página HTTPS não deve carregar recursos inseguros via HTTP (Mixed Content).");
    }

    @Test
    @DisplayName("CENÁRIO 8: Auditoria de Performance e Web Vitals")
    @SuppressWarnings("unchecked")
    public void auditarPerformanceWebVitals() {
        String script = "() => {\n" +
                "    const navEntries = performance.getEntriesByType('navigation');\n" +
                "    const nav = navEntries.length > 0 ? navEntries[0] : {};\n" +
                "    const paint = performance.getEntriesByType('paint') || [];\n" +
                "    const fcpEntry = paint.find(entry => entry.name === 'first-contentful-paint');\n" +
                "    const resources = performance.getEntriesByType('resource') || [];\n" +
                "    return {\n" +
                "        dnsTime: Math.max(0, Math.round((nav.domainLookupEnd || 0) - (nav.domainLookupStart || 0))),\n" +
                "        tcpTime: Math.max(0, Math.round((nav.connectEnd || 0) - (nav.connectStart || 0))),\n" +
                "        ttfb: Math.max(0, Math.round((nav.responseStart || 0) - (nav.requestStart || 0))),\n" +
                "        downloadTime: Math.max(0, Math.round((nav.responseEnd || 0) - (nav.responseStart || 0))),\n" +
                "        domInteractive: Math.max(0, Math.round(nav.domInteractive || 0)),\n" +
                "        domContentLoaded: Math.max(0, Math.round(nav.domContentLoadedEventEnd || 0)),\n" +
                "        loadTime: Math.max(0, Math.round(nav.loadEventEnd || 0)),\n" +
                "        fcp: fcpEntry ? Math.round(fcpEntry.startTime) : -1,\n" +
                "        resourcesCount: resources.length\n" +
                "    };\n" +
                "}";

        Map<String, Object> metrics = (Map<String, Object>) getPage().evaluate(script);

        long ttfb = ((Number) metrics.getOrDefault("ttfb", 0)).longValue();
        long fcp = ((Number) metrics.getOrDefault("fcp", -1)).longValue();
        long domContentLoaded = ((Number) metrics.getOrDefault("domContentLoaded", 0)).longValue();
        long loadTime = ((Number) metrics.getOrDefault("loadTime", 0)).longValue();
        long resourcesCount = ((Number) metrics.getOrDefault("resourcesCount", 0)).longValue();

        StringBuilder perfReport = new StringBuilder();
        perfReport.append("<div style='font-family: Arial, sans-serif;'>");
        perfReport.append("<h4>⚡ Relatório de Performance & Web Vitals</h4>");
        perfReport.append("<div style='display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 15px;'>");

        adicionarCardMetrica(perfReport, "TTFB (Time to First Byte)", ttfb + " ms", ttfb <= 800 ? "#5cb85c" : (ttfb <= 1800 ? "#f0ad4e" : "#d9534f"), "Meta: < 800ms");
        if (fcp >= 0) {
            adicionarCardMetrica(perfReport, "FCP (First Contentful Paint)", fcp + " ms", fcp <= 1800 ? "#5cb85c" : (fcp <= 3000 ? "#f0ad4e" : "#d9534f"), "Meta: < 1.8s");
        }
        adicionarCardMetrica(perfReport, "DOM Content Loaded", domContentLoaded + " ms", "#337ab7", "Estrutura pronta");
        adicionarCardMetrica(perfReport, "Load Completo", loadTime + " ms", loadTime <= 4000 ? "#5cb85c" : "#f0ad4e", "Meta: < 4.0s");
        adicionarCardMetrica(perfReport, "Requisições de Assets", String.valueOf(resourcesCount), "#777", "CSS, JS, Imagens");

        perfReport.append("</div></div>");
        getRelatorioTeste().info(perfReport.toString());

        // Verificação de tolerância para TTFB máximo
        assertTrue(ttfb < 8000, "O TTFB (Time to First Byte) excedeu a tolerância máxima. Servidor demorou: " + ttfb + "ms");
    }

    private String getAttributeSafely(String selector, String attribute) {
        var locator = getPage().locator(selector);
        if (locator.count() > 0) {
            return locator.first().getAttribute(attribute);
        }
        return null;
    }

    private void adicionarLinhaTabela(StringBuilder builder, String item, String status, String detalhes) {
        builder.append("<tr>")
                .append("<td style='padding: 8px; border: 1px solid #ddd; font-weight: bold;'>").append(item).append("</td>")
                .append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(status).append("</td>")
                .append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(detalhes).append("</td>")
                .append("</tr>");
    }

    private void adicionarCardMetrica(StringBuilder builder, String titulo, String valor, String cor, String meta) {
        builder.append("<div style='flex: 1; min-width: 150px; background: #fdfdfd; border-top: 4px solid ").append(cor)
                .append("; padding: 12px; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 8px;'>")
                .append("<div style='font-size: 11px; color: #666; font-weight: bold; text-transform: uppercase;'>").append(titulo).append("</div>")
                .append("<div style='font-size: 20px; font-weight: bold; color: ").append(cor).append("; margin: 4px 0;'>").append(valor).append("</div>")
                .append("<small style='color: #888;'>").append(meta).append("</small>")
                .append("</div>");
    }

    /**
     * Formata a lista de violações do Axe em um HTML legível e seguro para o relatório.
     *
     * @param violations A lista de regras violadas.
     * @return Uma string HTML com os detalhes das violações.
     */
    private String formatarViolacoesAxe(List<Rule> violations) {
        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append("<div style='font-family: Arial, sans-serif;'>");
        reportBuilder.append("<h4 style='color: #d9534f; margin-bottom: 10px;'>🚨 Violações de Acessibilidade Encontradas</h4>");

        for (Rule violation : violations) {
            String impact = violation.getImpact() != null ? violation.getImpact().toLowerCase() : "desconhecido";
            String badgeColor = "#777";
            if ("critical".equals(impact)) badgeColor = "#d9534f";
            else if ("serious".equals(impact)) badgeColor = "#f0ad4e";
            else if ("moderate".equals(impact)) badgeColor = "#ec971f";
            else if ("minor".equals(impact)) badgeColor = "#5bc0de";

            reportBuilder.append("<div style='border-left: 4px solid ").append(badgeColor)
                    .append("; padding: 10px; margin-bottom: 15px; background: #fafafa; border-radius: 4px;'>")
                    .append("<div><strong>Regra: </strong><code>").append(escapeHtml(violation.getId())).append("</code> ")
                    .append("<span style='background-color: ").append(badgeColor)
                    .append("; color: white; padding: 2px 8px; border-radius: 3px; font-size: 11px; text-transform: uppercase; font-weight: bold;'>")
                    .append(escapeHtml(impact)).append("</span></div>")
                    .append("<div style='margin-top: 5px;'><strong>Descrição: </strong>").append(escapeHtml(violation.getDescription())).append("</div>")
                    .append("<div><strong>Ajuda: </strong><a href='").append(escapeHtml(violation.getHelpUrl()))
                    .append("' target='_blank' rel='noopener noreferrer'>Ver documentação Deque</a></div>")
                    .append("<div style='margin-top: 8px;'><strong>Elementos afetados (").append(violation.getNodes().size()).append("):</strong><ul>");

            for (CheckedNode node : violation.getNodes()) {
                String selector = node.getTarget() != null ? node.getTarget().toString() : "N/A";
                reportBuilder.append("<li style='margin-bottom: 6px;'>")
                        .append("<strong>Seletor: </strong><code>").append(escapeHtml(selector)).append("</code><br/>");
                if (node.getHtml() != null && !node.getHtml().isBlank()) {
                    reportBuilder.append("<strong>HTML: </strong><code style='color: #c7254e; background-color: #f9f2f4;'>")
                            .append(escapeHtml(node.getHtml())).append("</code><br/>");
                }
                if (node.getFailureSummary() != null && !node.getFailureSummary().isBlank()) {
                    reportBuilder.append("<small style='color: #666;'><em>").append(escapeHtml(node.getFailureSummary().replace("\n", " "))).append("</em></small>");
                }
                reportBuilder.append("</li>");
            }
            reportBuilder.append("</ul></div></div>");
        }
        reportBuilder.append("</div>");
        return reportBuilder.toString();
    }

    /**
     * Escapa caracteres HTML para evitar problemas de renderização no relatório.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
