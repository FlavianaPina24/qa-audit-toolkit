package br.com.amoreco.audit;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
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
    @DisplayName("CENÁRIO 1: Verificação básica de acessibilidade")
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

        // Comparação por similaridade de pixels (fração de pixels diferentes)
        double threshold = 0.03; // tolerância padrão: 3% de pixels diferentes
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

    /**
     * Formata a lista de violações do Axe em um HTML legível e seguro para o relatório.
     *
     * @param violations A lista de regras violadas.
     * @return Uma string HTML com os detalhes das violações.
     */
    private String formatarViolacoesAxe(List<Rule> violations) {
        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append("<strong>Violações de Acessibilidade Encontradas:</strong><br/>");

        for (Rule violation : violations) {
            reportBuilder.append("<br/><div><strong>Regra: </strong>").append(escapeHtml(violation.getId()))
                    .append(" (Impacto: ").append(escapeHtml(violation.getImpact())).append(")<br/>")
                    .append("<strong>Descrição: </strong>").append(escapeHtml(violation.getDescription())).append("<br/>")
                    .append("<strong>Ajuda: </strong><a href='").append(escapeHtml(violation.getHelpUrl())).append("' target='_blank'>Ver documentação</a><br/>")
                    .append("<strong>Elementos afetados:</strong><ul>");

            for (Node node : violation.getNodes()) {
                String selector = node.getTarget().stream().findFirst().orElse("N/A");
                reportBuilder.append("<li><code>").append(escapeHtml(selector)).append("</code></li>");
            }
            reportBuilder.append("</ul></div>");
        }
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
