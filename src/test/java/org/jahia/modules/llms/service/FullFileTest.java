package org.jahia.modules.llms.service;

import org.jahia.modules.llms.service.internal.SiteWalker;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FullFileTest {

    private static final int ROOMY = 1_000_000;

    private static SiteWalker.Page page(String name) {
        return new SiteWalker.Page(name, "https://www.example.com/" + name + ".html", null, "/cms/render/live/en/sites/a/" + name + ".html");
    }

    private static long later() {
        return System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
    }

    /** Keeps what the file says of each page. */
    private static final class Seen implements LlmsService.Progress {
        private final List<String> states = new ArrayList<>();

        @Override
        public void started(List<SiteWalker.Page> pages) {
            // not what is tested here
        }

        @Override
        public void page(int index, GenerationJob.PageState state) {
            states.add(index + ":" + state);
        }
    }

    @Test
    void writesEachPageUnderItsTitleAndItsAddressInTheOrderOfTheSite() {
        List<SiteWalker.Page> pages = List.of(page("home"), page("about"));
        List<Future<String>> markdown = List.of(CompletableFuture.completedFuture("Welcome."), CompletableFuture.completedFuture("Who we are."));
        LlmsService.Generation file = FullFile.assemble(new StringBuilder("# Site\n"), pages, markdown, new FullFile.Bounds(later(), 10, ROOMY), false, new Seen());
        assertThat(file.content()).isEqualTo("""
                # Site

                ---

                # home

                Source: https://www.example.com/home.html

                Welcome.

                ---

                # about

                Source: https://www.example.com/about.html

                Who we are.
                """);
        assertThat(file.pages()).isEqualTo(2);
        assertThat(file.failedPages()).isEmpty();
        assertThat(file.truncated()).isFalse();
        assertThat(file.cutBy()).isNull();
        assertThat(file.skipped()).isZero();
    }

    @Test
    void aPageWithNothingToReadAddsNoBlockAndIsNotAFailure() {
        List<SiteWalker.Page> pages = List.of(page("form"), page("about"));
        List<Future<String>> markdown = List.of(CompletableFuture.completedFuture("  \n"), CompletableFuture.completedFuture("Who we are."));
        LlmsService.Generation file = FullFile.assemble(new StringBuilder("# Site\n"), pages, markdown, new FullFile.Bounds(later(), 10, ROOMY), false, new Seen());
        assertThat(file.content()).doesNotContain("# form").contains("# about");
        assertThat(file.pages()).isEqualTo(1);
        assertThat(file.failedPages()).isEmpty();
    }

    @Test
    void aPageThatCouldNotBeRenderedIsNamedAndTheNextOnesAreStillWritten() {
        List<SiteWalker.Page> pages = List.of(page("broken"), page("about"));
        List<Future<String>> markdown = List.of(CompletableFuture.failedFuture(new IOException("500")), CompletableFuture.completedFuture("Who we are."));
        Seen seen = new Seen();
        LlmsService.Generation file = FullFile.assemble(new StringBuilder("# Site\n"), pages, markdown, new FullFile.Bounds(later(), 10, ROOMY), false, seen);
        assertThat(file.content()).doesNotContain("# broken").contains("# about");
        assertThat(file.failedPages()).containsExactly("https://www.example.com/broken.html");
        assertThat(file.pages()).isEqualTo(1);
        assertThat(file.truncated()).isFalse();
        assertThat(seen.states).containsExactly("0:FAILED");
    }

    @Test
    void aPageThatDoesNotAnswerInTimeIsAFailureAndDoesNotHoldTheFile() {
        List<SiteWalker.Page> pages = List.of(page("slow"), page("about"));
        List<Future<String>> markdown = List.of(new CompletableFuture<>(), CompletableFuture.completedFuture("Who we are."));
        LlmsService.Generation file = FullFile.assemble(new StringBuilder("# Site\n"), pages, markdown, new FullFile.Bounds(later(), 0, ROOMY), false, new Seen());
        assertThat(file.failedPages()).containsExactly("https://www.example.com/slow.html");
        assertThat(file.content()).contains("# about");
    }

    @Test
    void whenTheFileIsFullThePageThatDoesNotFitAndTheOnesAfterItAreCountedAsLeftOut() {
        List<SiteWalker.Page> pages = List.of(page("home"), page("long"), page("about"));
        List<Future<String>> markdown = List.of(CompletableFuture.completedFuture("Welcome."),
                CompletableFuture.completedFuture("x".repeat(500)), CompletableFuture.completedFuture("Who we are."));
        Seen seen = new Seen();
        LlmsService.Generation file = FullFile.assemble(new StringBuilder("# Site\n"), pages, markdown, new FullFile.Bounds(later(), 10, 200), false, seen);
        assertThat(file.content()).contains("# home").doesNotContain("# long").doesNotContain("# about");
        assertThat(file.content().getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(200);
        assertThat(file.pages()).isEqualTo(1);
        assertThat(file.truncated()).isTrue();
        assertThat(file.cutBy()).isEqualTo(LlmsService.CUT_BY_SIZE);
        assertThat(file.skipped()).isEqualTo(2);
        // Left out, not failed: nothing went wrong with them.
        assertThat(file.failedPages()).isEmpty();
        assertThat(seen.states).containsExactly("1:SKIPPED", "2:SKIPPED");
    }

    @Test
    void whenTheTimeAllowedRanOutThePagesNotWrittenYetAreNamed() {
        List<SiteWalker.Page> pages = List.of(page("home"), page("about"));
        List<Future<String>> markdown = List.of(CompletableFuture.completedFuture("Welcome."), CompletableFuture.completedFuture("Who we are."));
        Seen seen = new Seen();
        LlmsService.Generation file = FullFile.assemble(new StringBuilder("# Site\n"), pages, markdown, new FullFile.Bounds(System.nanoTime() - 1, 10, ROOMY), false, seen);
        assertThat(file.content()).isEqualTo("# Site\n");
        assertThat(file.pages()).isZero();
        assertThat(file.truncated()).isTrue();
        assertThat(file.cutBy()).isEqualTo(LlmsService.CUT_BY_TIME);
        assertThat(file.failedPages()).containsExactly("https://www.example.com/home.html", "https://www.example.com/about.html");
        assertThat(seen.states).containsExactly("0:FAILED", "1:FAILED");
    }

    @Test
    void aSiteWithMorePagesThanWereListedIsSaidToBeCutByItsPages() {
        LlmsService.Generation file = FullFile.assemble(new StringBuilder("# Site\n"), List.of(page("home")),
                List.of(CompletableFuture.completedFuture("Welcome.")), new FullFile.Bounds(later(), 10, ROOMY), true, new Seen());
        assertThat(file.truncated()).isTrue();
        assertThat(file.cutBy()).isEqualTo(LlmsService.CUT_BY_PAGES);
        assertThat(file.pages()).isEqualTo(1);
    }
}
