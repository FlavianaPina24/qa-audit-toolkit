package br.com.amoreco.audit;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseTest {

    private static final String BROWSER_PROPERTY = "browser";
    private static final String HEADLESS_PROPERTY = "headless";

    protected static Playwright playwright;
    protected static Browser browser;
    // Usar ThreadLocal para garantir que cada thread de teste tenha seu próprio Context e Page
    private static final ThreadLocal<BrowserContext> contextThread = new ThreadLocal<>();
    private static final ThreadLocal<Page> pageThread = new ThreadLocal<>();
    private static final ThreadLocal<String> testNameThread = new ThreadLocal<>();

    private static ExtentReports extent;
    // Renomeado para clareza
    private static final ThreadLocal<ExtentTest> extentTestThread = new ThreadLocal<>();

    protected static final String REPORT_DIR = System.getProperty("report.dir", "target/test-reports");
    protected static final String SCREENSHOT_DIR = System.getProperty("screenshot.dir", REPORT_DIR + "/screenshots");
    protected static final String BASELINE_DIR = System.getProperty("baseline.dir", "src/test/resources/baseline-screenshots");

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
                // Acessa a Page da thread atual de forma segura
                Page currentPage = pageThread.get();
                getRelatorioTeste().fail(cause);
                if (currentPage != null) {
                    registrarEvidencia("Screenshot da Falha", currentPage.screenshot(new Page.ScreenshotOptions().setFullPage(true)));
                }
            }
        }
    };

    protected static ExtentTest getRelatorioTeste() {
        return extentTestThread.get();
    }

    @BeforeAll
    public static void iniciarNavegador() {
        DateTimeFormatter formatadorDataHora = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss");
        String dataHora = LocalDateTime.now().format(formatadorDataHora);
        ExtentSparkReporter spark = new ExtentSparkReporter("target/Auditoria_Website_" + dataHora + ".html");
        spark.config().setDocumentTitle("Relatório de Auditoria Web");
        spark.config().setReportName("QA Audit Toolkit");
        extent = new ExtentReports();
        ensureOutputDirectories();
        extent.attachReporter(spark);

        Map<String, String> env = new HashMap<>();
        env.put("PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS", "true");

        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));

        String browserName = System.getProperty(BROWSER_PROPERTY, "chromium").toLowerCase();
        boolean headless = Boolean.parseBoolean(System.getProperty(HEADLESS_PROPERTY, "true"));

        BrowserType browserType;
        switch (browserName) {
            case "chromium":
                browserType = playwright.chromium();
                break;
            case "firefox":
                browserType = playwright.firefox();
                break;
            case "webkit":
                browserType = playwright.webkit();
                break;
            default:
                throw new IllegalArgumentException("Navegador '" + browserName + "' não é suportado. Use 'chromium', 'firefox', ou 'webkit'.");
        }

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setArgs(List.of("--disable-dev-shm-usage", "--no-sandbox"));

        browser = browserType.launch(launchOptions);
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
        String testName = testInfo.getDisplayName().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        testNameThread.set(testName);

        ExtentTest test = extent.createTest(testInfo.getDisplayName());
        extentTestThread.set(test);

        BrowserContext newContext = browser.newContext();
        contextThread.set(newContext);

        Page newPage = newContext.newPage();
        pageThread.set(newPage);
    }

    // Método getter para que as classes filhas possam acessar a Page da thread atual
    protected Page getPage() {
        return pageThread.get();
    }

    protected static String getCurrentTestName() {
        return testNameThread.get();
    }

    protected static void registrarEvidencia(String titulo, byte[] printBytes) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String safeTestName = getCurrentTestName() != null ? getCurrentTestName() : "teste";
        String screenshotFileName = safeTestName + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")) + ".png";
        Path screenshotPath = Paths.get(SCREENSHOT_DIR, screenshotFileName);

        try {
            Files.write(screenshotPath, printBytes);
        } catch (IOException e) {
            if (getRelatorioTeste() != null) {
                getRelatorioTeste().warning("Não foi possível salvar screenshot em disco: " + e.getMessage());
            }
        }

        if (getRelatorioTeste() != null) {
            getRelatorioTeste().info(titulo + " (" + timestamp + ")<br/>Arquivo: " + screenshotPath.toAbsolutePath(),
                    MediaEntityBuilder.createScreenCaptureFromBase64String(Base64.getEncoder().encodeToString(printBytes)).build());
        }
    }

    @AfterEach
    public void fecharAba() {
        BrowserContext currentContext = contextThread.get();
        if (currentContext != null) {
            currentContext.close();
        }
        if (extent != null) {
            extent.flush();
        }
        extentTestThread.remove();
        contextThread.remove();
        pageThread.remove();
        testNameThread.remove();
    }

    private static void ensureOutputDirectories() {
        try {
            Files.createDirectories(Paths.get(REPORT_DIR));
            Files.createDirectories(Paths.get(SCREENSHOT_DIR));
        } catch (IOException ioe) {
            throw new RuntimeException("Falha ao criar diretórios de relatório ou screenshots: " + ioe.getMessage(), ioe);
        }
    }
}
