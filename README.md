# 🚀 QA Audit Toolkit

[![Java CI & QA Audit](https://github.com/FlavianaPina24/qa-audit-toolkit/actions/workflows/maven.yml/badge.svg)](https://github.com/FlavianaPina24/qa-audit-toolkit/actions/workflows/maven.yml)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net)
[![Playwright](https://img.shields.io/badge/Playwright-1.46-2EAD33?logo=playwright&logoColor=white)](https://playwright.dev/java/)
[![Axe-Core](https://img.shields.io/badge/Axe--Core-4.10-blue)](https://www.deque.com/axe/)
[![JUnit 5](https://img.shields.io/badge/JUnit-5.10-25A162?logo=junit5&logoColor=white)](https://junit.org/junit5/)
[![ExtentReports](https://img.shields.io/badge/ExtentReports-5.1-orange)](http://extentreports.com)
[![GitHub Pages](https://img.shields.io/badge/Dashboard-GitHub%20Pages-2ea44f?logo=githubpages&logoColor=white)](https://flavianapina24.github.io/qa-audit-toolkit/)
[![AI Powered](https://img.shields.io/badge/AI%20Agent-Google%20Gemini-8a2387?logo=googlegemini&logoColor=white)](https://ai.google.dev/)

**QA Audit Toolkit** é um framework moderno e extensível de **Auditoria Automatizada de Qualidade Web**, construído sobre **Java, Playwright, Axe-Core e JUnit 5**, potencializado por um **Agente de IA (Google Gemini)** para consultoria e sugestão de correções em tempo real.

Ele foi projetado para avaliar sites e aplicações web sob múltiplos pilares de engenharia de qualidade: **Acessibilidade Digital (WCAG 2.1)**, **SEO Técnico**, **Performance & Web Vitals**, **Segurança Web**, **Regressão Visual** e **Integridade de Links**.

---

## 🤖 Agente de IA Consultor de Qualidade (Google Gemini)

O toolkit conta com um **Agente de IA Integrado (`AiAdvisorService`)** que atua como um consultor de QA & DevSecOps Blue Team diretamente no relatório:

- **Acessibilidade (WCAG 2.1):** Diagnostica a causa raiz das violações encontradas pelo Axe-Core e sugere o código HTML/CSS exato para correção.
- **Segurança Web & DevSecOps:** Analisa cabeçalhos ausentes e gera a configuração exata pronta para Nginx, Apache ou Cloudflare (HSTS, CSP, X-Frame-Options), além de prevenção contra Reverse Tabnabbing.
- **SEO Técnico:** Avalia títulos, meta descriptions e canonical tags, sugerindo snippets completos de metatags otimizadas.
- **Performance & Web Vitals:** Analisa métricas de TTFB, FCP e requisições, orientando sobre compressão Brotli/Gzip, Edge Caching e formatos de imagem modernos.
- **Modo Híbrido:** Funciona em modo heurístico offline por padrão e se conecta automaticamente ao **Google Gemini 2.5 Flash** quando a variável de ambiente `GEMINI_API_KEY` estiver configurada!

---

## 🎯 Pilares e Cenários de Auditoria

| # | Cenário | Descrição |
|---|---|---|
| **Cenário 1** | **Acessibilidade com Axe-Core (A11y)** | Varredura completa com motor Deque Axe-Core em conformidade com **WCAG 2.0 e 2.1 (A e AA)**. Identifica violações por impacto (*Crítico*, *Sério*, *Moderado*, *Menor*), detalhando o seletor, trecho HTML e documentação corretiva. |
| **Cenário 2** | **Estrutura Semântica de Acessibilidade** | Valida atributos obrigatórios de acessibilidade nativa, como `lang` no elemento `<html>` e presença do landmark `<main>`. |
| **Cenário 3** | **Estrutura da Página Inicial** | Validação de carregamento, integridade do título da página (`<title>`) e presença de títulos de cabeçalho (`h1`, `h2`). |
| **Cenário 4** | **Imagens e Textos Alternativos** | Identifica imagens sem atributo `alt` ou com `alt` vazio que comprometem a navegação por leitores de tela e indexação de busca. |
| **Cenário 5** | **Links e Rotas Válidas** | Detecta links quebrados, tags `<a>` sem `href`, URLs vazias ou com chamadas obsoletas de pseudo-protocolo `javascript:`. |
| **Cenário 6** | **SEO Técnico** | Avalia tags essenciais (`<title>`, `<meta name="description">`), canonical tag, meta robots, tags Open Graph (`og:title`, `og:image`), hierarquia de cabeçalhos e disponibilidade de `robots.txt` e `sitemap.xml`. |
| **Cenário 7** | **Segurança Web & Headers HTTP** | Inspeciona cabeçalhos de proteção (`HSTS`, `CSP`, `X-Frame-Options`, `X-Content-Type-Options`), proteção contra Reverse Tabnabbing em links `target="_blank"` (`rel="noopener noreferrer"`), e mitigação de conteúdo misto (*Mixed Content*). |
| **Cenário 8** | **Performance & Web Vitals** | Coleta métricas de carregamento em tempo real via Navigation Timing API: **TTFB** (*Time to First Byte*), **FCP** (*First Contentful Paint*), **DOM Content Loaded**, tempo total de load e contagem de requisições de assets. |
| **Cenário 9** | **Regressão Visual Automatizada** | Comparação de telas pixel a pixel contra baselines de referência, com estabilização de fontes/animações, tolerância adaptativa (`threshold`) e geração de mapas visuais de diff destacando alterações em vermelho. |

---

## 📊 Relatórios de Execução (ExtentReports)

Ao final de cada execução, um relatório interativo HTML é gerado automaticamente em `target/`:

- **Caminho:** `target/Auditoria_Website_DD-MM-YYYY_HH-mm-ss.html`
- **Evidências:** Screenshots automáticos de página inteira salvos em `target/test-reports/screenshots/` e embutidos em Base64 no próprio relatório para facilitar compartilhamento.
- **Painéis:** Tabelas comparativas de SEO, cartões métricos de performance colorizados por meta e detalhes exatos das violações do Axe-Core.

---

## 🛠️ Pré-requisitos

- **Java JDK 17** ou superior (testado também em JDK 21).
- **Apache Maven 3.9+**.
- Conexão com a internet para download dos navegadores gerenciados pelo Playwright.

---

## 🚀 Como Executar

### 1. Clonar o Repositório
```bash
git clone https://github.com/flavianapina/qa-audit-toolkit.git
cd qa-audit-toolkit
```

### 2. Instalar os Navegadores do Playwright
```bash
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install --with-deps"
```

### 3. Rodar Todos os Testes
```bash
mvn test
```

### 4. Customizações por Parâmetros CLI

#### Auditar Outra URL
```bash
mvn test -DtargetUrl="https://seu-site.com.br"
```

#### Atualizar os Snapshots de Regressão Visual (Criar Novo Baseline)
```bash
mvn test -Dtest=AuditTest#auditarConsistenciaVisual -Dupdate.snapshots=true
```

#### Customizar Regras do Axe-Core
```bash
# Ignorar regras específicas conhecidas
mvn test -Daxe.disabled.rules="button-name,color-contrast"

# Modo estrito (falha em qualquer violação, mesmo leves)
mvn test -Daxe.strict=true
```

#### Trocar de Navegador e Modo de Execução
```bash
# Executar no Firefox
mvn test -Dbrowser=firefox

# Executar com interface gráfica visível (headed)
mvn test -Dheadless=false
```

---

## ⚙️ CI/CD (GitHub Actions)

O projeto inclui um fluxo de integração contínua configurado em [`.github/workflows/maven.yml`](.github/workflows/maven.yml):

- Disparado automaticamente em cada `push` ou `pull_request` nas branches `main` e `master`.
- Prepara o ambiente Ubuntu com JDK 17 e cache de dependências Maven.
- Instala os navegadores do Playwright (`install --with-deps`).
- Executa a suíte de auditoria.
- **Armazena os relatórios:** Publica como artefato (`qa-audit-reports`) os relatórios HTML, capturas de tela e relatórios Surefire para consulta no GitHub.
- **Dashboard Online (GitHub Pages):** Publica automaticamente a versão mais recente do relatório interativo no GitHub Pages em:
  👉 **`https://flavianapina24.github.io/qa-audit-toolkit/`**

> **Dica de Configuração Inicial no GitHub:**  
> Acesse **Settings** > **Pages** no repositório do GitHub e em **Source** selecione **"GitHub Actions"**. A partir disso, todo push atualizará a página pública instantaneamente!

---

## 📄 Licença

Distribuído sob a licença MIT. Consulte `LICENSE` para mais informações.

