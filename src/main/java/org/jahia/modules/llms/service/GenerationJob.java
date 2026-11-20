package org.jahia.modules.llms.service;

import org.jahia.modules.llms.service.internal.SiteWalker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * One generation of llms-full.txt running in the background, and how far it is: the pages it renders,
 * what became of each, and the file once it is done. The panel reads it while it runs, so an editor
 * sees the pages go by instead of waiting on a request that says nothing for a minute.
 *
 * <p>Written by the threads that render the pages and read by the requests of the panel: thread-safe.</p>
 */
public final class GenerationJob implements LlmsService.Progress {

    /** What became of a page. */
    public enum PageState {
        /** Not rendered yet. */
        PENDING,
        /** Rendered, and written in the file. */
        RENDERED,
        /** Rendered, with nothing to read: left out of the file. */
        EMPTY,
        /** Could not be rendered, or not in time: left out of the file. */
        FAILED
    }

    /**
     * A page and what became of it.
     *
     * @param title the title of the page
     * @param url   its public URL
     * @param state what became of it
     */
    public record PageProgress(String title, String url, PageState state) {
    }

    /**
     * The job as it stands at one moment.
     *
     * @param started    whether the pages to render are known
     * @param pages      the pages, in the order of the file
     * @param done       how many are no longer pending
     * @param finished   whether the job is over, with a file or with an error
     * @param error      why it failed, or null
     * @param generation the file, once finished without error
     */
    public record Snapshot(boolean started, List<PageProgress> pages, int done, boolean finished, String error,
                           LlmsService.Generation generation) {
    }

    private final String id;
    private final String siteKey;
    private final long createdNanos = System.nanoTime();
    private volatile List<SiteWalker.Page> pages;
    private volatile AtomicReferenceArray<PageState> states;
    private volatile LlmsService.Generation generation;
    private volatile String error;
    private volatile boolean finished;

    GenerationJob(String id, String siteKey) {
        this.id = id;
        this.siteKey = siteKey;
    }

    /**
     * @return the identifier the panel asks the progress with
     */
    public String id() {
        return id;
    }

    String siteKey() {
        return siteKey;
    }

    long ageNanos() {
        return System.nanoTime() - createdNanos;
    }

    @Override
    public void started(List<SiteWalker.Page> listed) {
        AtomicReferenceArray<PageState> all = new AtomicReferenceArray<>(listed.size());
        for (int i = 0; i < listed.size(); i++) {
            all.set(i, PageState.PENDING);
        }
        // The states first: a reader that sees the pages must find their states.
        this.states = all;
        this.pages = List.copyOf(listed);
    }

    @Override
    public void page(int index, PageState state) {
        AtomicReferenceArray<PageState> all = states;
        if (all != null && index >= 0 && index < all.length()) {
            all.set(index, state);
        }
    }

    void finish(LlmsService.Generation result) {
        this.generation = result;
        this.finished = true;
    }

    void fail(String message) {
        this.error = message;
        this.finished = true;
    }

    /**
     * @return the job as it stands now
     */
    public Snapshot snapshot() {
        List<SiteWalker.Page> listed = pages;
        AtomicReferenceArray<PageState> all = states;
        // Read before the pages: a job seen as finished has all its states written.
        boolean over = finished;
        List<PageProgress> out = new ArrayList<>();
        int done = 0;
        if (listed != null && all != null) {
            for (int i = 0; i < listed.size(); i++) {
                PageState state = all.get(i);
                if (state != PageState.PENDING) {
                    done++;
                }
                out.add(new PageProgress(listed.get(i).title(), listed.get(i).url(), state));
            }
        }
        return new Snapshot(listed != null, out, done, over, error, over ? generation : null);
    }
}
