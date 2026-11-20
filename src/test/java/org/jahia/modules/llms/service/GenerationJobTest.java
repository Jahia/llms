package org.jahia.modules.llms.service;

import org.jahia.modules.llms.service.internal.SiteWalker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationJobTest {

    private static SiteWalker.Page page(String title) {
        return new SiteWalker.Page(title, "https://www.example.com/" + title + ".html", null, "/cms/render/live/en/sites/a/" + title + ".html");
    }

    @Test
    void saysNothingOfThePagesBeforeTheyAreKnown() {
        GenerationJob.Snapshot snapshot = new GenerationJob("id", "a").snapshot();
        assertThat(snapshot.started()).isFalse();
        assertThat(snapshot.pages()).isEmpty();
        assertThat(snapshot.finished()).isFalse();
    }

    @Test
    void followsEachPageInTheOrderOfTheFile() {
        GenerationJob job = new GenerationJob("id", "a");
        job.started(List.of(page("home"), page("about"), page("empty"), page("broken")));
        // Pages are rendered in parallel: they report in any order.
        job.page(2, GenerationJob.PageState.EMPTY);
        job.page(0, GenerationJob.PageState.RENDERED);

        GenerationJob.Snapshot snapshot = job.snapshot();
        assertThat(snapshot.started()).isTrue();
        assertThat(snapshot.done()).isEqualTo(2);
        assertThat(snapshot.finished()).isFalse();
        assertThat(snapshot.generation()).isNull();
        assertThat(snapshot.pages()).extracting(GenerationJob.PageProgress::title).containsExactly("home", "about", "empty", "broken");
        assertThat(snapshot.pages()).extracting(GenerationJob.PageProgress::state).containsExactly(
                GenerationJob.PageState.RENDERED, GenerationJob.PageState.PENDING,
                GenerationJob.PageState.EMPTY, GenerationJob.PageState.PENDING);
    }

    @Test
    void aPageLeftOutBecauseTheFileIsFullStaysSo() {
        GenerationJob job = new GenerationJob("id", "a");
        job.started(List.of(page("home"), page("about")));
        job.page(0, GenerationJob.PageState.RENDERED);
        job.page(1, GenerationJob.PageState.SKIPPED);
        // The page was still being rendered when the file filled up: its thread, stopped, reports a failure.
        job.page(1, GenerationJob.PageState.FAILED);

        GenerationJob.Snapshot snapshot = job.snapshot();
        assertThat(snapshot.pages()).extracting(GenerationJob.PageProgress::state)
                .containsExactly(GenerationJob.PageState.RENDERED, GenerationJob.PageState.SKIPPED);
        // Left out is done: the count reaches the total, and the bar its end.
        assertThat(snapshot.done()).isEqualTo(2);
    }

    @Test
    void givesTheFileOnlyOnceFinished() {
        GenerationJob job = new GenerationJob("id", "a");
        job.started(List.of(page("home")));
        job.page(0, GenerationJob.PageState.RENDERED);
        LlmsService.Generation file = new LlmsService.Generation("# Site", 1, List.of(), false);
        job.finish(file);

        GenerationJob.Snapshot snapshot = job.snapshot();
        assertThat(snapshot.finished()).isTrue();
        assertThat(snapshot.error()).isNull();
        assertThat(snapshot.generation()).isSameAs(file);
        assertThat(snapshot.done()).isEqualTo(1);
    }

    @Test
    void aFailureEndsTheJobWithoutAFile() {
        GenerationJob job = new GenerationJob("id", "a");
        job.fail("The file could not be generated");
        GenerationJob.Snapshot snapshot = job.snapshot();
        assertThat(snapshot.finished()).isTrue();
        assertThat(snapshot.error()).isEqualTo("The file could not be generated");
        assertThat(snapshot.generation()).isNull();
    }

    @Test
    void ignoresAPageOutOfTheList() {
        GenerationJob job = new GenerationJob("id", "a");
        job.page(0, GenerationJob.PageState.RENDERED);
        job.started(List.of(page("home")));
        job.page(5, GenerationJob.PageState.RENDERED);
        job.page(-1, GenerationJob.PageState.RENDERED);
        assertThat(job.snapshot().done()).isZero();
    }
}
