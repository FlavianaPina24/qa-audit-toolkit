package br.com.amoreco.audit;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import com.aventstack.extentreports.ExtentTest;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class BaseTest {

    private static final String BROWSER_PROPERTY = "browser";
    private static final String HEADLESS_PROPERTY = "headless";

    protected static Playwright playwright;
    protected static Browser browser;
    protected BrowserContext context;
    protected Page page;

    private static ExtentReports extent;
    private static final ThreadLocal<ExtentTest> relatorioTesteThread = new ThreadLocal<>();

    @RegisterExtension
    static TestWatcher watcher = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            if (getRelatorioTeste() != null) {
                getRelatorioTeste().pass("Auditoria concluída com sucesso!");
            }
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            if (getRelatorioTeste() != null) {
                getRelatorioTeste().fail(cause);
            }
        }
    };

    protected static ExtentTest getRelatorioTeste() {
        return relatorioTesteThread.get();
    }

    @BeforeAll
    public static void iniciarNavegador() {
        DateTimeFormatter formatadorDataHora = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss");
        String dataHora = LocalDateTime.now().format(formatadorDataHora);
        ExtentSparkReporter spark = new ExtentSparkReporter("target/Auditoria_Website_" + dataHora + ".html");
        spark.config().setDocumentTitle("Relatório de Auditoria Web");
        spark.config().setReportName("QA Audit Toolkit");
        extent = new ExtentReports();
        extent.attachReporter(spark);

        Map<String, String> env = new HashMap<>();
        env.put("PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS", "true");

        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));

        String browserName = System.getProperty(BROWSER_PROPERTY, "chromium").toLowerCase();
        boolean headless = Boolean.parseBoolean(System.getProperty(HEADLESS_PROPERTY, "true"));

        BrowserType browserType;
        switch (browserName) {
            case "firefox":
                browserType = playwright.firefox();
                break;
            case "webkit":
                browserType = playwright.webkit();
                break;
            case "chromium":
                browserType = playwright.chromium();
                break;
            default:
                throw new IllegalArgumentException("Navegador não suportado: " + browserName + ". Opções válidas: chromium, firefox, webkit.");
        }

        browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(headless));
    }

    @AfterAll
    public static void fecharNavegador() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        if (extent != null) {
            extent.flush();
        }
    }

    protected void configurarTeste(TestInfo testInfo) {
        ExtentTest test = extent.createTest(testInfo.getDisplayName());
        relatorioTesteThread.set(test);
        context = browser.newContext();
        page = context.newPage();
    }

    protected void registrarEvidencia(String titulo, byte[] printBytes) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        getRelatorioTeste().info(titulo + " (" + timestamp + ")",
                MediaEntityBuilder.createScreenCaptureFromBase64String(Base64.getEncoder().encodeToString(printBytes)).build());
    }

    @AfterEach
    public void fecharAba() {
        context.close();
        relatorioTesteThread.remove();
    }
}
