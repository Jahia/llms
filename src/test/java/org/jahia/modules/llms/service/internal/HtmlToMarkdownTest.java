package org.jahia.modules.llms.service.internal;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlToMarkdownTest {

    private static final String BASE = "https://www.example.com/en/about.html";

    private static String convert(String html, String title) {
        return HtmlToMarkdown.convert(Jsoup.parse(html, BASE), title, UnaryOperator.identity());
    }

    @Test
    void keepsMainAndDropsChrome() {
        String md = convert("<html><body><header><nav><a href='/'>Home</a></nav></header>"
                + "<main><h1>About us</h1><p>We make <strong>tools</strong>.</p></main>"
                + "<footer>Legal</footer><script>alert(1)</script></body></html>", "About us");
        assertThat(md).isEqualTo("We make **tools**.");
    }

    @Test
    void withoutMainDropsHeaderNavFooter() {
        String md = convert("<body><header>Logo</header><nav>Menu</nav><div><h2>Team</h2><p>Ten people</p></div>"
                + "<footer>Legal</footer></body>", "About");
        assertThat(md).isEqualTo("## Team\n\nTen people");
    }

    @Test
    void writesHeadingsBelowThePageTitle() {
        String md = convert("<main><h1>Other title</h1><h3>Detail</h3></main>", "About");
        assertThat(md).isEqualTo("## Other title\n\n### Detail");
    }

    @Test
    void resolvesLinksAndImagesAgainstThePublicUrl() {
        String md = convert("<main><p>See <a href='/en/contact.html'>contact</a> and "
                + "<a href='#top'>top</a>. <img src='/files/a.png' alt='A chart'><img src='/b.png' alt=''></p></main>", "x");
        assertThat(md).isEqualTo("See [contact](https://www.example.com/en/contact.html) and top. "
                + "![A chart](https://www.example.com/files/a.png)");
    }

    @Test
    void mapsLinksThroughTheLinkMap() {
        String md = HtmlToMarkdown.convert(Jsoup.parse("<main><a href='/cms/render/live/en/sites/x/a.html'>A</a></main>", BASE),
                "x", href -> href.endsWith("/a.html") ? "https://www.example.com/a" : href);
        assertThat(md).isEqualTo("[A](https://www.example.com/a)");
    }

    @Test
    void writesNestedLists() {
        String md = convert("<main><ul><li>One<ul><li>One.1</li></ul></li><li>Two</li></ul>"
                + "<ol><li>First</li><li>Second</li></ol></main>", "x");
        assertThat(md).isEqualTo("- One\n  - One.1\n- Two\n\n1. First\n2. Second");
    }

    @Test
    void flattensCardsInListItems() {
        String md = convert("<main><ul><li><div><h3><a href='/a.html'>Card</a></h3><p>Teaser text.</p></div></li></ul></main>", "x");
        assertThat(md).isEqualTo("- [Card](https://www.example.com/a.html): Teaser text.");
    }

    @Test
    void dropsBreadcrumbsButKeepsOtherMenusInMain() {
        String md = convert("<main><nav aria-label='Fil d&#39;Ariane'><a href='/'>Accueil</a></nav>"
                + "<nav aria-label='Plan du site'><ul><li><a href='/b.html'>B</a></li></ul></nav></main>", "x");
        assertThat(md).isEqualTo("- [B](https://www.example.com/b.html)");
    }

    @Test
    void writesTablesQuotesAndCode() {
        String md = convert("<main><table><tr><th>Plan</th><th>Price</th></tr><tr><td>Pro | EU</td><td>10</td></tr></table>"
                + "<blockquote><p>Quoted</p></blockquote><pre>a  b\n c</pre></main>", "x");
        assertThat(md).isEqualTo("| Plan | Price |\n| --- | --- |\n| Pro \\| EU | 10 |\n\n> Quoted\n\n```\na  b\n c\n```");
    }

    @Test
    void dropsHiddenAndFormContent() {
        String md = convert("<main><p hidden>Secret</p><span class='sr-only'>skip</span><form><label>Name</label>"
                + "<input></form><p aria-hidden='true'>icon</p><p>Visible</p></main>", "x");
        assertThat(md).isEqualTo("Visible");
    }

    @Test
    void keepsLineBreaksAndMixedInlineContent() {
        String md = convert("<main><div>Line one<br>Line   two<p>Para</p>tail <em>end</em></div></main>", "x");
        assertThat(md).isEqualTo("Line one\nLine two\n\nPara\n\ntail *end*");
    }

    @Test
    void writesOnlyWebMailAndPhoneLinks() {
        String md = convert("<main><p><a href='JavaScript:alert(1)'>run</a> <a href='data:text/html,x'>data</a> "
                + "<a href='vbscript:x'>vb</a> <a href='MAILTO:a@example.com'>mail</a> <a href='/ok'>ok</a></p></main>", "T");
        assertThat(md).isEqualTo("run data vb [mail](mailto:a@example.com) [ok](https://www.example.com/ok)");
    }

    @Test
    void dropsImagesThatAreNotOnTheWeb() {
        String md = convert("<main><p><img alt='a' src='data:image/png;base64,AAAA'><img alt='b' src='javascript:x'>"
                + "<img alt='c' src='/c.png'></p></main>", "T");
        assertThat(md).isEqualTo("![c](https://www.example.com/c.png)");
    }

    @Test
    void aLinkTargetCannotCloseTheLinkOrBreakTheLine() {
        String md = convert("<main><p><a href='/a(b) c'>x</a></p></main>", "T");
        assertThat(md).isEqualTo("[x](https://www.example.com/a%28b%29%20c)");
    }

    @Test
    void longRunsOfWhiteSpaceAreConvertedInLinearTime() {
        String spaces = " ".repeat(300_000);
        String html = "<main><pre>code" + spaces + "x</pre><ul><li><p>a" + spaces + "b</p></li></ul></main>";
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            String md = convert(html, "T");
            assertThat(md).startsWith("```\ncode").endsWith("- a b");
        });
    }
}
