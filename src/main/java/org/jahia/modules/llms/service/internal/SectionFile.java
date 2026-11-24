package org.jahia.modules.llms.service.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The llms.txt of a section: a file placed under the path of a page, which covers the pages below that
 * page ({@code /sites/a/home/docs/llms.txt} covers {@code /sites/a/home/docs/...}), as the llms.txt
 * proposal defines a file in a sub-path.
 *
 * @param nodePath the repository path of the page the file belongs to
 * @param full     true for llms-full.txt
 */
public record SectionFile(String nodePath, boolean full) {

    /**
     * The forms Jahia's own rewrite rules give the URL, then a page path made of plain segments: what a
     * page name is in Jahia. A path with an encoded character, a dot segment or an empty segment is not
     * read at all, so nothing in it can be understood two ways.
     */
    private static final Pattern FILE = Pattern.compile(
            "^(?:/cms(?:/render/live)?)?(?:/[a-z]{2,3}(?:_[A-Z]{2})?)?(/sites/[A-Za-z0-9_-]{1,64}/[A-Za-z0-9._/-]+)/llms(-full)?\\.txt$");
    private static final Pattern PAGE_PATH = Pattern.compile("^/sites/[A-Za-z0-9_-]{1,64}/[A-Za-z0-9._/-]+$");

    /**
     * @param rawPath a path as it was asked for, from the root of the context
     * @return the file it names, or null when it is not the file of a section
     */
    public static SectionFile of(String rawPath) {
        if (rawPath == null) {
            return null;
        }
        Matcher m = FILE.matcher(rawPath);
        return m.matches() && hasPlainSegments(m.group(1)) ? new SectionFile(m.group(1), m.group(2) != null) : null;
    }

    /**
     * @param nodePath the repository path of a page
     * @return the address of its llms.txt from the root of the context, or null when the path cannot be
     *         written as one (a page name with a character an address would have to encode)
     */
    public static String hrefOf(String nodePath) {
        return nodePath != null && PAGE_PATH.matcher(nodePath).matches() && hasPlainSegments(nodePath) ? nodePath + "/llms.txt" : null;
    }

    /**
     * The patterns accept any run of the characters of a page name and of slashes, read in one pass
     * whatever the length of the path. What they cannot say is checked here: no empty segment, none that
     * starts with a dot.
     */
    private static boolean hasPlainSegments(String path) {
        return !path.contains("//") && !path.contains("/.") && !path.endsWith("/");
    }
}
