import React, {useDeferredValue, useMemo} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Typography} from '@jahia/moonstone';
import {marked} from 'marked';
import createDOMPurify from 'dompurify';
import {MODULE} from './module';
import styles from './Llms.module.css';

// Llms-full.txt can hold megabytes: a page of that size as HTML would stall the browser. What is shown
// is cut at a line, and the reader is told.
export const PREVIEW_LIMIT = 300 * 1024;
// The card of the file is titled with an h2: the headings of the file come under it, two levels down.
const HEADING_SHIFT = 2;
const HEADING = /<(\/?)h([1-6])\b/g;
// A path is written as it is: nothing here is inserted as HTML.
const PLAIN = {interpolation: {escapeValue: false}};

/** The part of a file the preview shows: all of it, or its first lines up to the limit. */
export const previewed = text => {
    if (text.length <= PREVIEW_LIMIT) {
        return text;
    }

    const cut = text.lastIndexOf('\n', PREVIEW_LIMIT);
    return text.slice(0, cut > 0 ? cut : PREVIEW_LIMIT);
};

/**
 * Markdown as HTML that is safe to put in the page. The text is an editor's, or comes from the pages of
 * the site: it may hold HTML, which Markdown lets through. Everything but plain formatting is taken out:
 * no script, no style, no form, no image, no id or tab stop that would collide with the panel.
 */
const sanitizerFor = newTab => {
    const purify = createDOMPurify(globalThis);
    purify.addHook('afterSanitizeAttributes', node => {
        if (node.tagName === 'A' && node.hasAttribute('href')) {
            node.setAttribute('target', '_blank');
            node.setAttribute('rel', 'noopener noreferrer');
            const note = node.ownerDocument.createElement('span');
            note.setAttribute('data-llms-new-tab', '');
            note.textContent = ` (${newTab})`;
            node.appendChild(note);
        }
    });
    return html => purify.sanitize(html, {
        USE_PROFILES: {html: true},
        FORBID_TAGS: ['img', 'picture', 'svg', 'style', 'form', 'input', 'button', 'select', 'textarea', 'iframe', 'video', 'audio'],
        FORBID_ATTR: ['id', 'name', 'tabindex', 'style', 'class', 'contenteditable', 'autofocus']
    });
};

const demote = html => html.replace(HEADING, (match, closing, level) => `<${closing}h${Math.min(6, Number(level) + HEADING_SHIFT)}`);

/**
 * A file as a person reads it: its Markdown rendered. Read only; the text is edited in the editor. The
 * box scrolls, so it takes the focus, and is the only thing in it that scrolls.
 */
export const MarkdownPreview = ({id, fileName, text}) => {
    const {t} = useTranslation(MODULE);
    // Deferred: turning the preview on, or typing before it, never waits for a large file to be rendered.
    const deferred = useDeferredValue(text);
    const newTab = t('newTab');
    const sanitize = useMemo(() => sanitizerFor(newTab), [newTab]);
    const html = useMemo(() => sanitize(demote(marked.parse(previewed(deferred), {async: false, gfm: true}))), [deferred, sanitize]);
    const isCut = text.length > PREVIEW_LIMIT;
    const noteId = `${id}-note`;

    return (
        <div id={id}
             role="region"
             tabIndex={0}
             className={styles.preview}
             aria-label={t('preview.label', {file: fileName, ...PLAIN})}
             aria-describedby={isCut ? noteId : undefined}
             data-llms-role="preview"
        >
            {isCut && (
                <Typography id={noteId} className={styles.muted} data-llms-role="preview-cut">{t('preview.cut')}</Typography>
            )}
            {text.trim() === '' && <Typography className={styles.muted}>{t('preview.empty')}</Typography>}
            {/* Sanitized above: scripts, styles, forms, images, ids and tab stops are taken out. */}
            {/* eslint-disable-next-line react/no-danger */}
            <div dangerouslySetInnerHTML={{__html: html}} className={styles.previewBody}/>
        </div>
    );
};

MarkdownPreview.propTypes = {
    id: PropTypes.string.isRequired,
    fileName: PropTypes.string.isRequired,
    text: PropTypes.string.isRequired
};
