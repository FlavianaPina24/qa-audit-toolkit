package br.com.amoreco.audit;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.Node;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuditTest extends BaseTest {

    private final String targetUrl = System.getProperty("targetUrl", "https://flavianapina.com.br");

    @BeforeEach
    public void navegarParaAuditoria(TestInfo testInfo) {
        super.configurarTeste(testInfo);
        getRelatorioTeste().info("Iniciando auditoria na URL: " + targetUrl);
        page.navigate(targetUrl);
    }

    @Test
    @DisplayName("CENÁRIO 1: Auditoria de Acessibilidade (A11y) com Axe-Core")
    public void auditarAcessibilidade() {
        String disabledRules = System.getProperty("axe.disabled.rules", "color-contrast,link-name");

        AxeResults accessibilityReport = new AxeBuilder(page)
                .withTags(Arrays.asList("wcag2a", "wcag2aa"))
                .disableRules(Arrays.stream(disabledRules.split(","))
                                     .map(String::trim)
                                     .collect(Collectors.toList()))
                .analyze();

        List<Rule> violations = accessibilityReport.getViolations();

        if (!violations.isEmpty()) {
            StringBuilder reportDetails = new StringBuilder("<strong>Violações de Acessibilidade Encontradas:</strong><br/>");
            for (Rule violation : violations) {
                reportDetails.append("<br/><strong>Regra: </strong>").append(violation.getId())
                        .append(" (Impacto: ").append(violation.getImpact()).append(")<br/>")
                        .append("<strong>Descrição: </strong>").append(violation.getDescription()).append("<br/>")
                        .append("<strong>Ajuda: </strong><a href='").append(violation.getHelpUrl()).append("' target='_blank'>")
                        .append(violation.getHelpUrl()).append("</a><br/>")
                        .append("<strong>Elementos afetados:</strong><br/>");

                for (Node node : violation.getNodes()) {
                    reportDetails.append(" - Seletor: <code>").append(node.getTarget().get(0)).append("</code><br/>");
                }
            }

            // Usamos .fail() para dar mais destaque no relatório ExtentReports
            getRelatorioTeste().fail("<pre>" + reportDetails.toString() + "</pre>");

            registrarEvidencia("Evidência - Falha de Acessibilidade",
                    page.screenshot(new Page.ScreenshotOptions().setFullPage(true)));
        }

        assertTrue(violations.isEmpty(),
                () -> "Foram encontradas " + violations.size() + " violações de acessibilidade. Veja o relatório para detalhes.");
    }

    @Test
@DisplayName("CENÁRIO 2: Auditoria de Regressão Visual")
public void auditarConsistenciaVisual() throws IOException {
    // Captura a tela atual
    byte[] screenshotAtual = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));

    Path baselinePath = Paths.get("baseline-visual.png");

    if (!Files.exists(baselinePath)) {
        // Primeira execução: cria o baseline
        Files.write(baselinePath, screenshotAtual);
        getRelatorioTeste().info("Baseline visual criado.");
    } else {
        // Compara com o baseline existente
        byte[] baseline = Files.readAllBytes(baselinePath);
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                baseline,
                screenshotAtual,
                "Diferença visual detectada em relação ao baseline!"
        );
    }
}
}