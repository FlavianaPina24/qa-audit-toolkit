package br.com.amoreco.audit.ai;

import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Rule;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Agente de IA Consultor de Qualidade Web (QA & Blue Team DevSecOps).
 * Integra com a Google Gemini API para diagnosticar falhas e sugerir correções de código em tempo real.
 */
public class AiAdvisorService {

    private static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson GSON = new Gson();

    public static String getApiKey() {
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getProperty("gemini.api.key");
        }
        return (key != null && !key.isBlank()) ? key.trim() : null;
    }

    public static boolean isAiEnabled() {
        return getApiKey() != null;
    }

    /**
     * Envia o prompt para a Gemini API e retorna o texto gerado.
     */
    public static String askGemini(String prompt) {
        String apiKey = getApiKey();
        if (apiKey == null) {
            return null;
        }

        try {
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + DEFAULT_MODEL + ":generateContent?key=" + apiKey;

            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", prompt);

            JsonArray parts = new JsonArray();
            parts.add(textPart);

            JsonObject content = new JsonObject();
            content.add("parts", parts);

            JsonArray contents = new JsonArray();
            contents.add(content);

            JsonObject requestBody = new JsonObject();
            requestBody.add("contents", contents);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject jsonResponse = GSON.fromJson(response.body(), JsonObject.class);
                JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
                if (candidates != null && candidates.size() > 0) {
                    JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                    JsonObject contentObj = firstCandidate.getAsJsonObject("content");
                    JsonArray resParts = contentObj.getAsJsonArray("parts");
                    if (resParts != null && resParts.size() > 0) {
                        return resParts.get(0).getAsJsonObject().get("text").getAsString();
                    }
                }
            } else {
                System.err.println("Gemini API retornou status " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Falha ao comunicar com Gemini API: " + e.getMessage());
        }

        return null;
    }

    /**
     * Gera recomendações de correção para violações de Acessibilidade (Axe-Core).
     */
    public static String getAccessibilityRecommendations(List<Rule> violations) {
        if (violations == null || violations.isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Você é um Engenheiro de Qualidade de Software (QA/SDET) e especialista em Acessibilidade Web (WCAG 2.1).\n");
        prompt.append("Analise as seguintes violações encontradas pelo Axe-Core e forneça um plano de ação sucinto em Português do Brasil com:\n");
        prompt.append("1. Diagnóstico da causa raiz;\n");
        prompt.append("2. Código HTML/CSS exato sugerido para corrigir o elemento;\n");
        prompt.append("3. Impacto na experiência do usuário e conformidade legal.\n\n");
        prompt.append("Violações detectadas:\n");

        for (Rule r : violations) {
            prompt.append("- Regra: ").append(r.getId()).append(" (Impacto: ").append(r.getImpact()).append(")\n");
            prompt.append("  Descrição: ").append(r.getDescription()).append("\n");
            for (CheckedNode node : r.getNodes()) {
                prompt.append("  Elemento: ").append(node.getHtml()).append("\n");
            }
        }

        String aiResponse = askGemini(prompt.toString());

        if (aiResponse == null || aiResponse.isBlank()) {
            aiResponse = getAccessibilityFallback(violations);
        }

        return formatAiCard("Consultoria de Acessibilidade (WCAG 2.1)", markdownToHtml(aiResponse));
    }

    /**
     * Gera recomendações de Segurança Web (Blue Team DevSecOps).
     */
    public static String getSecurityRecommendations(Map<String, String> headers, int unsafeLinks, int mixedContent) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Você é um Engenheiro DevSecOps / Blue Team especialista em Segurança de Aplicações Web.\n");
        prompt.append("Analise os cabeçalhos HTTP e apontamentos de segurança abaixo de uma aplicação web e forneça em Português do Brasil:\n");
        prompt.append("1. Avaliação dos riscos detectados (ex: Clickjacking, MIME-sniffing, XSS, Reverse Tabnabbing);\n");
        prompt.append("2. Configuração exata recomendada para Nginx, Apache ou Cloudflare com os cabeçalhos de segurança (HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy);\n");
        prompt.append("3. Correção rápida no HTML para links externos com target='_blank'.\n\n");

        prompt.append("Dados coletados:\n");
        prompt.append("- HSTS presente: ").append(headers.containsKey("strict-transport-security")).append("\n");
        prompt.append("- CSP presente: ").append(headers.containsKey("content-security-policy")).append("\n");
        prompt.append("- X-Frame-Options presente: ").append(headers.containsKey("x-frame-options")).append("\n");
        prompt.append("- X-Content-Type-Options presente: ").append(headers.containsKey("x-content-type-options")).append("\n");
        prompt.append("- Links externos sem noopener/noreferrer: ").append(unsafeLinks).append("\n");
        prompt.append("- Conteúdo misto (HTTP em HTTPS): ").append(mixedContent).append("\n");

        String aiResponse = askGemini(prompt.toString());

        if (aiResponse == null || aiResponse.isBlank()) {
            aiResponse = getSecurityFallback(headers, unsafeLinks, mixedContent);
        }

        return formatAiCard("Consultoria Blue Team & DevSecOps", markdownToHtml(aiResponse));
    }

    /**
     * Gera recomendações de SEO Técnico.
     */
    public static String getSeoRecommendations(String title, String description, String canonical, int h1Count) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Você é um especialista sênior em SEO Técnico e Engenharia Web.\n");
        prompt.append("Analise os seguintes elementos de SEO de uma página e forneça recomendações práticas e trechos de código meta tags em Português do Brasil:\n");
        prompt.append("- Title: ").append(title != null ? title : "Ausente").append("\n");
        prompt.append("- Meta Description: ").append(description != null ? description : "Ausente").append("\n");
        prompt.append("- Canonical: ").append(canonical != null ? canonical : "Ausente").append("\n");
        prompt.append("- Quantidade de títulos H1: ").append(h1Count).append("\n");

        String aiResponse = askGemini(prompt.toString());

        if (aiResponse == null || aiResponse.isBlank()) {
            aiResponse = getSeoFallback(title, description, canonical, h1Count);
        }

        return formatAiCard("Consultoria de SEO Técnico", markdownToHtml(aiResponse));
    }

    /**
     * Gera recomendações de Performance e Web Vitals.
     */
    public static String getPerformanceRecommendations(long ttfb, long fcp, long loadTime, long resourcesCount) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Você é um Engenheiro de Performance Web especialista em Core Web Vitals.\n");
        prompt.append("Analise as métricas de carregamento em tempo real coletadas via Navigation Timing API:\n");
        prompt.append("- TTFB: ").append(ttfb).append(" ms\n");
        prompt.append("- FCP: ").append(fcp).append(" ms\n");
        prompt.append("- Tempo total de load: ").append(loadTime).append(" ms\n");
        prompt.append("- Total de assets requisitados: ").append(resourcesCount).append("\n\n");
        prompt.append("Forneça diagnósticos e dicas avançadas de otimização (Caching, CDN, minificação, compressão Brotli/Gzip, Critical Rendering Path).\n");

        String aiResponse = askGemini(prompt.toString());

        if (aiResponse == null || aiResponse.isBlank()) {
            aiResponse = getPerformanceFallback(ttfb, fcp, loadTime, resourcesCount);
        }

        return formatAiCard("Consultoria de Performance & Web Vitals", markdownToHtml(aiResponse));
    }

    /**
     * Formata o card moderno no ExtentReports com badge de IA e layout visual elegante.
     */
    public static String formatAiCard(String category, String contentHtml) {
        String badge = isAiEnabled() ? "⚡ Powered by Gemini AI" : "💡 Heurística Blue Team (Modo Offline)";
        String badgeColor = isAiEnabled() ? "background: linear-gradient(135deg, #8a2387, #e94057, #f27121);" : "background: #4b6cb7;";

        return "<div class='ai-advisor-card' style='margin-top: 15px; border-radius: 8px; border: 1px solid #e1e4e8; background: #ffffff; box-shadow: 0 4px 12px rgba(0,0,0,0.05); overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif;'>" +
                "<div style='background: #24292e; color: #ffffff; padding: 10px 16px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px;'>" +
                "<div style='display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 13.5px;'>" +
                "<span style='font-size: 18px;'>🤖</span> <span>Agente de IA: " + category + "</span>" +
                "</div>" +
                "<span style='" + badgeColor + " color: white; padding: 4px 12px; border-radius: 12px; font-size: 11px; font-weight: bold; white-space: nowrap;'>" + badge + "</span>" +
                "</div>" +
                "<div style='padding: 16px; font-size: 13.5px; line-height: 1.6; color: #24292e;'>" +
                contentHtml +
                "</div>" +
                "</div>";
    }

    // --- Respostas Heurísticas Especializadas (Offline Fallback em Markdown) ---

    private static String getAccessibilityFallback(List<Rule> violations) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Diagnóstico de Engenharia:** Foram identificadas oportunidades de enriquecimento semântico no DOM para tecnologias assistivas (leitores de tela e navegação por teclado).\n\n");
        sb.append("**Pontos de Atenção Detectados:**\n");
        for (Rule r : violations) {
            sb.append("- **Regra `").append(r.getId()).append("`:** ").append(r.getHelp()).append("\n");
        }
        sb.append("\n**Recomendações Práticas:**\n");
        sb.append("1. Garanta que todos os botões e links possuam nomes acessíveis através de texto visível ou atributo `aria-label`.\n");
        sb.append("2. Verifique o contraste de cores entre texto e fundo para atingir no mínimo 4.5:1 (conforme WCAG 2.1 nível AA).\n");
        sb.append("3. Mantenha landmarks semânticos como `<header>`, `<nav>`, `<main>` e `<footer>` bem estruturados.");
        return sb.toString();
    }

    private static String getSecurityFallback(Map<String, String> headers, int unsafeLinks, int mixedContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Diagnóstico Blue Team & DevSecOps:** Recomendações de mitigação preventiva para riscos comuns da Web (OWASP Top 10).\n\n");
        sb.append("**Configuração de Cabeçalhos HTTP para Nginx:**\n");
        sb.append("```nginx\n");
        sb.append("add_header Strict-Transport-Security \"max-age=31536000; includeSubDomains; preload\" always;\n");
        sb.append("add_header X-Frame-Options \"SAMEORIGIN\" always;\n");
        sb.append("add_header X-Content-Type-Options \"nosniff\" always;\n");
        sb.append("add_header Referrer-Policy \"strict-origin-when-cross-origin\" always;\n");
        sb.append("add_header Permissions-Policy \"geolocation=(), camera=(), microphone=()\" always;\n");
        sb.append("```\n\n");

        if (unsafeLinks > 0) {
            sb.append("**Proteção contra Reverse Tabnabbing:**\n");
            sb.append("Adicione o atributo `rel=\"noopener noreferrer\"` em todas as tags `<a>` que usam `target=\"_blank\"` para evitar manipulações não autorizadas do objeto `window.opener`.\n");
        } else {
            sb.append("✅ **Reverse Tabnabbing:** Todos os links externos já utilizam proteções de isolamento.\n");
        }

        if (mixedContent > 0) {
            sb.append("🚨 **Mixed Content:** Existem recursos HTTP carregados em página HTTPS. Atualize todas as URLs para HTTPS.\n");
        }

        return sb.toString();
    }

    private static String getSeoFallback(String title, String description, String canonical, int h1Count) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Diagnóstico de SEO Técnico:** Otimização para rastreabilidade e indexação por buscadores (Googlebot, Bingbot).\n\n");
        sb.append("**Recomendações e Boas Práticas:**\n");
        sb.append("- **Título da Página:** Mantenha entre 30 e 65 caracteres com as palavras-chave primárias no início.\n");
        sb.append("- **Meta Description:** Mantenha entre 70 e 160 caracteres com chamada para ação clara (CTA).\n");
        if (canonical == null || canonical.isBlank()) {
            sb.append("- **Link Canonical Ausente:** Adicione `<link rel=\"canonical\" href=\"https://flavianapina.com.br/\" />` dentro de `<head>` para consolidar a autoridade de domínio e prevenir duplicação de páginas.\n");
        }
        sb.append("- **Hierarquia de Títulos:** Mantenha exatamente um título `<h1>` por página como tema principal.\n\n");
        sb.append("**Exemplo de Meta Tags Recomendado:**\n");
        sb.append("```html\n");
        sb.append("<link rel=\"canonical\" href=\"https://flavianapina.com.br/\" />\n");
        sb.append("<meta name=\"robots\" content=\"index, follow\" />\n");
        sb.append("<meta property=\"og:type\" content=\"website\" />\n");
        sb.append("```");
        return sb.toString();
    }

    private static String getPerformanceFallback(long ttfb, long fcp, long loadTime, long resourcesCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Diagnóstico de Performance & Web Vitals:**\n\n");
        sb.append("- **TTFB (Time to First Byte):** ").append(ttfb).append(" ms ");
        if (ttfb <= 800) {
            sb.append("(✅ Excelente tempo de resposta inicial do servidor)\n");
        } else {
            sb.append("(⚠️ Considere ativar Edge Caching no Cloudflare ou otimizar consultas do servidor)\n");
        }
        sb.append("- **Requisições de Assets:** ").append(resourcesCount).append(" arquivos carregados.\n\n");
        sb.append("**Ações de Otimização Recomendadas:**\n");
        sb.append("1. **Compressão:** Habilite `Brotli` ou `Gzip` no servidor para reduzir em até 70% o tamanho dos arquivos JS e CSS transferidos.\n");
        sb.append("2. **Formatos Modernos de Imagem:** Converta arquivos PNG/JPEG para `WebP` ou `AVIF` e aplique `loading=\"lazy\"` em imagens abaixo da dobra.\n");
        sb.append("3. **Pré-conexão DNS:** Utilize `<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\" />` para recursos externos essenciais.");
        return sb.toString();
    }

    /**
     * Conversor simples de Markdown para HTML básico (suporta bold, code blocks, listas e headers).
     */
    private static String markdownToHtml(String markdown) {
        if (markdown == null) return "";

        String html = markdown.replace("\r\n", "\n").replace("\r", "\n");
        // Escapar caracteres especiais que possam conflitar
        html = html.replace("<", "&lt;").replace(">", "&gt;");

        // Code blocks - styled with !important to defeat ExtentReports Spark's global pre color: #222 !important
        html = html.replaceAll("```[a-zA-Z]*\n([\\s\\S]*?)```", "<pre class='ai-code-block' style='background: #1e1e1e !important; color: #f0f6fc !important; padding: 12px; border-radius: 6px; font-size: 12px; border: 1px solid #30363d; overflow-x: auto;'><code style='color: #f0f6fc !important; background: transparent !important; font-family: Consolas, \"Courier New\", monospace;'>$1</code></pre>");
        // Inline code
        html = html.replaceAll("`([^`]+)`", "<code style='background: #f0f2f5 !important; color: #d63384 !important; padding: 2px 5px; border-radius: 3px; font-family: Consolas, monospace;'>$1</code>");
        // Bold
        html = html.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        // Headers
        html = html.replaceAll("### (.*)", "<h5 style='margin-top: 10px; margin-bottom: 5px; font-weight: bold;'>$1</h5>");
        html = html.replaceAll("## (.*)", "<h4 style='margin-top: 12px; margin-bottom: 6px; font-weight: bold;'>$1</h4>");
        // Linebreaks
        html = html.replace("\n", "<br/>");

        return html;
    }
}
