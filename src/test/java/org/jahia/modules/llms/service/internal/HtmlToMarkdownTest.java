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

    private static String withImages(String html, String title) {
        return HtmlToMarkdown.convert(Jsoup.parse(html, BASE), title, UnaryOperator.identity(), true);
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
        String md = withImages("<main><p>See <a href='/en/contact.html'>contact</a> and "
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

    /**
     * A documentation template wraps the menu of the section and the table of contents of the page in
     * main, beside the article: they are navigation, and every page of the section would repeat them.
     */
    @Test
    void dropsTheMenuOfASectionAndTheTableOfContentsPutInsideMain() {
        String md = convert("<main><div><aside><nav aria-label='Docs navigation'><ul>"
                + "<li><a href='/a.html'>Introduction</a></li><li><a href='/b.html'>Setting up</a></li>"
                + "<li>Java<ul><li><a href='/c.html'>Views</a></li><li><a href='/d.html'>Rules</a></li></ul></li></ul></nav></aside>"
                + "<article><h1>Making a hero section</h1><p>" + LONG + "</p><h2>Working with content</h2><p>" + LONG + "</p>"
                + "<p>Next: <a href='/e.html'>The about page</a></p></article>"
                + "<nav id='toc'><strong>In this page</strong><ul></ul><a href='#'>Back to top</a></nav></div></main>", "Making a hero section");
        assertThat(md).doesNotContain("Introduction").doesNotContain("Setting up").doesNotContain("Views")
                .doesNotContain("In this page").doesNotContain("Back to top");
        assertThat(md).startsWith(LONG).contains("## Working with content").endsWith("Next: [The about page](https://www.example.com/e.html)");
    }

    /** The menu is the page when it is most of its text, whatever stands around it. */
    @Test
    void keepsAMenuThatIsThePage() {
        String md = convert("<main><h1>Site map</h1><p>Every page of the site.</p><nav><ul>"
                + "<li><a href='/a.html'>Products and services of the company</a></li>"
                + "<li><a href='/b.html'>Documentation and tutorials for developers</a></li>"
                + "<li><a href='/c.html'>Contact and offices around the world</a></li></ul></nav></main>", "Site map");
        assertThat(md).contains("[Products and services of the company](https://www.example.com/a.html)")
                .contains("[Contact and offices around the world](https://www.example.com/c.html)");
    }

    @Test
    void dropsATagLeftOpenAsTextAndKeepsTextAboutTags() {
        // As served by a page whose rich text is broken: the tag is text, between a paragraph and a list item.
        String md = convert("<main><p>Service provided by:</p><p>Google LLC</p>&lt;ul <li>Google Ireland</li>"
                + "<p>Use &lt;ul&gt; for a list, and a &lt; b comparison.</p></main>", "x");
        assertThat(md).doesNotContain("<ul \n").doesNotContain("<ul\n")
                .contains("Google LLC").contains("Google Ireland")
                .contains("Use <ul> for a list, and a < b comparison.");
        assertThat(md.lines().map(String::trim)).doesNotContain("<ul");
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
        String md = withImages("<main><p><img alt='a' src='data:image/png;base64,AAAA'><img alt='b' src='javascript:x'>"
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

    private static final String LONG = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(6).trim();

    @Test
    void keepsTheTitleAndThePanelOfAnAccordion() {
        // The title is a button in a heading, the panel is hidden until the button opens it.
        String md = convert("<main><h3><button aria-expanded='false' aria-controls='p1'>Opening hours</button></h3>"
                + "<div id='p1' hidden><p>Monday to Friday, 9 to 5.</p></div>"
                + "<h3><button aria-controls='p2'>Prices</button></h3>"
                + "<div id='p2' aria-hidden='true' style='display:none'><p>Free.</p></div></main>", "T");
        assertThat(md).isEqualTo("### Opening hours\n\nMonday to Friday, 9 to 5.\n\n### Prices\n\nFree.");
    }

    @Test
    void keepsEveryTabPanelUnderTheNameOfItsTab() {
        String md = convert("<main><div role='tablist'><button role='tab' aria-controls='a'>Students</button>"
                + "<button role='tab' aria-controls='b'>Teachers</button></div>"
                + "<div id='a' role='tabpanel'><p>For students.</p></div>"
                + "<div id='b' role='tabpanel' hidden><p>For teachers.</p></div></main>", "T");
        assertThat(md).isEqualTo("**Students**\n\nFor students.\n\n**Teachers**\n\nFor teachers.");
    }

    @Test
    void keepsTheSlidesOfACarouselAndDropsDecorativeIcons() {
        String md = convert("<main><p>Intro <span aria-hidden='true'>*</span></p><div id='c'>"
                + "<div aria-roledescription='slide'><p>One</p></div>"
                + "<div aria-roledescription='slide' aria-hidden='true'><p>Two</p></div></div>"
                + "<div style='display: none'>Leftover</div></main>", "T");
        assertThat(md).isEqualTo("Intro\n\nOne\n\nTwo");
    }

    @Test
    void neverKeepsADialog() {
        String md = convert("<main><p>Page</p><button data-bs-target='#m'>Sign in</button>"
                + "<div class='modal' id='m' aria-hidden='true'><p>Your password</p></div></main>", "T");
        assertThat(md).isEqualTo("Page");
    }

    @Test
    void keepsTheContentOfAFormWrappedAroundThePage() {
        String md = convert("<main><form action='/x'><h2>Team</h2><p>Ten people</p>"
                + "<input type='hidden' name='t' value='1'><button>Send</button></form></main>", "T");
        assertThat(md).isEqualTo("## Team\n\nTen people");
    }

    @Test
    void passesOverAMainThatOnlyWrapsATitle() {
        String md = convert("<body><header><a href='/'>Logo</a></header><main><h1>About</h1></main>"
                + "<div id='content'><p>" + LONG + "</p></div><footer>Legal</footer></body>", "About");
        assertThat(md).isEqualTo(LONG);
    }

    @Test
    void takesTheSingleArticleAndKeepsItsOwnHeaderAndAside() {
        String md = convert("<body><header>Site</header><div><article><header><h1>News</h1><p>Lead paragraph.</p></header>"
                + "<p>" + LONG + "</p><aside><p>Note for readers.</p></aside></article></div>"
                + "<aside><p>Related links</p></aside><footer>Legal</footer></body>", "News");
        assertThat(md).isEqualTo("Lead paragraph.\n\n" + LONG + "\n\nNote for readers.");
    }

    @Test
    void withoutAnyUsualPlaceKeepsTheHeaderOfASectionAndDropsThePageHeader() {
        String md = convert("<body><header><nav>Menu</nav></header><section><header><h2>Offer</h2></header>"
                + "<p>Details</p></section><footer>Legal</footer></body>", "T");
        assertThat(md).isEqualTo("## Offer\n\nDetails");
    }

    @Test
    void keepsAnEmbeddedVideoAsALinkAndALazyImage() {
        String md = withImages("<main><p><iframe title='Presentation video' src='https://video.example.com/embed/1'></iframe></p>"
                + "<p><iframe src='https://tracker.example.com/x'></iframe></p>"
                + "<p><img alt='Team' src='data:image/gif;base64,AAAA' data-src='/team.jpg'></p></main>", "T");
        assertThat(md).isEqualTo("[Presentation video](https://video.example.com/embed/1)\n\n![Team](https://www.example.com/team.jpg)");
    }

    @Test
    void writesADefinitionListAsTermsAndDefinitions() {
        String md = convert("<main><dl><dt>Phone</dt><dd>01 23 45 67 89</dd></dl></main>", "T");
        assertThat(md).isEqualTo("**Phone**\n\n01 23 45 67 89");
    }

    @Test
    void keepsTheContentBesideMain() {
        // An intro above <main> and a section after it: all of it is the page.
        String md = convert("<body><header role='banner'><nav>Menu</nav></header>"
                + "<div class='hero'><h1>Our school</h1><p>Founded in 1950, in the heart of the city.</p></div>"
                + "<main><h2>Courses</h2><p>" + LONG + "</p></main>"
                + "<section><h2>Contact</h2><p>Write to us at the address below, we answer within two days.</p></section>"
                + "<footer role='contentinfo'>Legal</footer></body>", "Our school");
        assertThat(md).isEqualTo("Founded in 1950, in the heart of the city.\n\n## Courses\n\n" + LONG
                + "\n\n## Contact\n\nWrite to us at the address below, we answer within two days.");
    }

    @Test
    void keepsMainAloneWhenLittleSitsOutsideIt() {
        String md = convert("<body><div><a href='#c'>Skip to content</a></div><main id='c'><p>" + LONG + "</p></main>"
                + "<div><p>Share</p></div></body>", "T");
        assertThat(md).isEqualTo(LONG);
    }

    @Test
    void dropsChromeThatIsOnlyNamedAndKeepsContentNamedAlike() {
        String md = convert("<body><div id='header'><a href='/'>Logo</a> Menu of the site with many entries</div>"
                + "<div class='navbar'>Products Services Company</div>"
                + "<div class='content-area'><div class='card-header'><h2>Offer</h2></div><p>" + LONG + "</p></div>"
                + "<div class='site-footer'>Legal notice and other links of the footer</div></body>", "T");
        assertThat(md).isEqualTo("## Offer\n\n" + LONG);
    }

    @Test
    void readsAPageOfATemplateWithoutLandmarks() {
        // The skeleton of a page of dx-base-demo-templates: no <main>, no <header>, no <footer>.
        String md = convert("<body class='header-fixed header-fixed-space-v2'><div class='wrapper'>"
                + "<div class='header-v8 header-sticky'><div class='blog-topbar'><a id='login' role='button'>Login</a>"
                + "<span class='user-box'>&nbsp;|&nbsp;</span><a id='login' role='button'>Login</a></div>"
                + "<div class='navbar mega-menu' role='navigation'><ul class='nav navbar-nav'><li><a href='/home.html'>Home</a></li>"
                + "<li class='active'><a href='/p1.html'>P1</a></li></ul></div></div>"
                + "<div class='container margin-top-20'><div class='row'><div class='col-md-9'><p>yeah!</p></div>"
                + "<div class='col-md-3'></div></div></div>"
                + "<div class='footer-v1'><div class='footer'></div><div class='copyright'><p>2026 &copy; All Rights Reserved."
                + "<a href='#'>Privacy Policy</a> | <a href='#'>Terms of Service</a></p>"
                + "<a href='http://www.jahia.com'><img src='/img/poweredByJahia.png' alt='Powered by Jahia'></a></div></div>"
                + "<div id='loginForm' class='modal fade' role='dialog'><form><label>Username</label><input></form></div>"
                + "</div></body>", "P1");
        assertThat(md).isEqualTo("yeah!");
    }

    @Test
    void leavesImagesOutUnlessAskedFor() {
        String html = "<main><p>Our team <img alt='Team photo' src='/team.jpg'> at work.</p>"
                + "<p><a href='/home.html'><img alt='Logo' src='/logo.png'></a></p>"
                + "<figure><img alt='Chart' src='/chart.png'><figcaption>Sales in 2025</figcaption></figure></main>";
        // A link that held only an image has nothing left to say: it goes with the image.
        assertThat(convert(html, "T")).isEqualTo("Our team at work.\n\nSales in 2025");
        assertThat(withImages(html, "T")).contains("![Team photo](https://www.example.com/team.jpg)")
                .contains("[![Logo](https://www.example.com/logo.png)](https://www.example.com/home.html)");
    }
}
