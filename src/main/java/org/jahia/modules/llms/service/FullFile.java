package org.jahia.modules.llms.service;

import org.jahia.modules.llms.service.internal.SiteWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * llms-full.txt while it is put together: the pages are rendered side by side, and written here one
 * after the other, in the order of the site, until they are all in, the time allowed runs out or the
 * file is full. What is left out is counted and said, never dropped in silence.
 *
 * <p>Used by one thread, the one that writes the file.</p>
 */
final class FullFile {

    private static final Logger LOGGER = LoggerFactory.getLogger(FullFile.class);

    private final StringBuilder text;
    private final int maxBytes;
    private final List<String> failed = new ArrayList<>();
    private int bytes;
    private int written;
    private boolean truncated;
    private String cutBy;
    private int skipped;
    /** Whether a limit closed the file: no page is written after that. */
    private boolean closed;

    private FullFile(StringBuilder header, int maxBytes, boolean truncated) {
        this.text = header;
        this.maxBytes = maxBytes;
        this.bytes = header.toString().getBytes(StandardCharsets.UTF_8).length;
        this.truncated = truncated;
        this.cutBy = truncated ? LlmsService.CUT_BY_PAGES : null;
    }

    /**
     * What a generation may take.
     *
     * @param deadlineNanos   when it must be over, as {@link System#nanoTime()} counts
     * @param pageWaitSeconds how long one page is waited for at most
     * @param maxBytes        the size the file may not exceed
     */
    record Bounds(long deadlineNanos, long pageWaitSeconds, int maxBytes) {
    }

    /**
     * Writes the pages under the header as they are rendered.
     *
     * @param header    the start of the file: the title of the site and what it is
     * @param pages     the pages, in the order of the file
     * @param markdown  the Markdown of each page, being rendered
     * @param bounds    the time and the size allowed
     * @param truncated whether the site has more pages than were listed
     * @param progress  told what becomes of each page
     * @return the file, with what was left out of it
     */
    static LlmsService.Generation assemble(StringBuilder header, List<SiteWalker.Page> pages, List<Future<String>> markdown,
                                           Bounds bounds, boolean truncated, LlmsService.Progress progress) {
        FullFile file = new FullFile(header, bounds.maxBytes(), truncated);
        for (int i = 0; i < pages.size() && !file.closed; i++) {
            long left = TimeUnit.NANOSECONDS.toSeconds(bounds.deadlineNanos() - System.nanoTime());
            if (left <= 0) {
                file.outOfTime(pages, i, progress);
            } else {
                file.write(pages, i, body(markdown.get(i), pages.get(i), Math.min(bounds.pageWaitSeconds(), left), file.failed), progress);
            }
        }
        return new LlmsService.Generation(file.text.toString(), file.written, file.failed, file.truncated, file.cutBy, file.skipped);
    }

    /** Out of time: the pages not rendered yet are named, not waited for. */
    private void outOfTime(List<SiteWalker.Page> pages, int from, LlmsService.Progress progress) {
        truncated = true;
        cutBy = LlmsService.CUT_BY_TIME;
        closed = true;
        for (int rest = from; rest < pages.size(); rest++) {
            failed.add(pages.get(rest).url());
            progress.page(rest, GenerationJob.PageState.FAILED);
        }
    }

    /**
     * @param body the Markdown of the page, or null when it could not be rendered
     */
    private void write(List<SiteWalker.Page> pages, int index, String body, LlmsService.Progress progress) {
        if (body == null) {
            // Also a page that did not answer in time, which its own thread has not reported.
            progress.page(index, GenerationJob.PageState.FAILED);
        }
        // A page with nothing to read (an empty page, a page that is only a form) adds no block:
        // a title and an address with nothing under them tell a reader nothing.
        if (body == null || body.isBlank()) {
            return;
        }
        SiteWalker.Page page = pages.get(index);
        String block = "\n---\n\n# " + page.title() + "\n\nSource: " + page.url() + "\n\n" + body + "\n";
        int size = block.getBytes(StandardCharsets.UTF_8).length;
        if (bytes + size > maxBytes) {
            full(pages, index, progress);
            return;
        }
        text.append(block);
        bytes += size;
        written++;
    }

    /**
     * The file is full: this page and the ones after it are said to be left out, not left unsaid. The
     * ones still being rendered are stopped by the caller, and are not failures.
     */
    private void full(List<SiteWalker.Page> pages, int from, LlmsService.Progress progress) {
        truncated = true;
        cutBy = LlmsService.CUT_BY_SIZE;
        closed = true;
        skipped = pages.size() - from;
        for (int rest = from; rest < pages.size(); rest++) {
            progress.page(rest, GenerationJob.PageState.SKIPPED);
        }
    }

    private static String body(Future<String> future, SiteWalker.Page page, long waitSeconds, List<String> failed) {
        try {
            return future.get(waitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failed.add(page.url());
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.debug("Could not render {} for llms-full.txt", page.renderPath(), e);
            failed.add(page.url());
        }
        return null;
    }
}
