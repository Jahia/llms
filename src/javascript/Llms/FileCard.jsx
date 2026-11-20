import React, {useDeferredValue, useMemo} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Banner, Button, Chip, Loader, OpenInNew, Reload, Textarea, Typography, Undo} from '@jahia/moonstone';
import {MODULE, PLACEHOLDER} from './module';
import styles from './Llms.module.css';

const LINK = /\]\((https?:\/\/[^)\s]+)\)/g;

const size = value => {
    const bytes = new TextEncoder().encode(value).length;
    return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
};

/** Counted without building an array of every line: llms-full.txt can hold megabytes. */
const lineCount = value => {
    if (!value) {
        return 0;
    }

    let lines = 1;
    for (let at = value.indexOf('\n'); at !== -1; at = value.indexOf('\n', at + 1)) {
        lines++;
    }

    return lines;
};

/** Three passes over the whole text: computed when the text changes, not on every render. */
const statsOf = value => ({
    lines: lineCount(value),
    links: (value.match(LINK) || []).length,
    size: size(value)
});

/** Where the file stands, from what is typed, stored and served. */
const status = (value, saved, isCurrent) => {
    if (value !== saved) {
        return {key: 'unsaved', color: 'accent'};
    }

    if (!saved.trim()) {
        return {key: 'none', color: 'default'};
    }

    if (saved.trim() === PLACEHOLDER) {
        return {key: 'placeholder', color: 'warning'};
    }

    return isCurrent ? {key: 'published', color: 'success'} : {key: 'notPublished', color: 'warning'};
};

export const FileCard = ({file, value, saved, isServed, isCurrent, generation, isGenerating, isGenerateEnabled, liveUrl, onChange, onGenerate, onRevert}) => {
    const {t} = useTranslation(MODULE);
    const state = status(value, saved, isCurrent);
    // Deferred: while the editor types in a large file, React keeps the keystroke ahead of the counts.
    const counted = useDeferredValue(value);
    const stats = useMemo(() => statsOf(counted), [counted]);
    const textareaId = `llms-editor-${file.id}`;
    const descriptionId = `llms-file-description-${file.id}`;

    return (
        <section className={styles.card}
                 role="tabpanel"
                 id="llms-tabpanel"
                 aria-labelledby={`llms-tab-${file.id}`}
                 aria-busy={isGenerating}
                 data-llms-file={file.id}
        >
            <div className={styles.cardHead}>
                <div className={styles.cardTitle}>
                    <Typography variant="heading" component="h2">{file.fileName}</Typography>
                    <Chip label={t(`status.${state.key}`)} color={state.color} data-llms-status={state.key}/>
                </div>
                <div className={styles.actions}>
                    <Button data-llms-role="generate"
                            icon={<Reload aria-hidden="true"/>}
                            label={t(saved.trim() ? 'regenerate' : 'generate')}
                            isDisabled={!isGenerateEnabled}
                            onClick={onGenerate}/>
                    <Button data-llms-role="revert"
                            variant="ghost"
                            icon={<Undo aria-hidden="true"/>}
                            label={t('revert')}
                            isDisabled={value === saved || isGenerating}
                            onClick={onRevert}/>
                    <Button data-llms-role="view"
                            variant="ghost"
                            icon={<OpenInNew aria-hidden="true"/>}
                            label={t('view')}
                            aria-label={`${t('view')} (${t('newTab')})`}
                            isDisabled={!isServed}
                            onClick={() => globalThis.open(liveUrl, '_blank', 'noopener')}/>
                </div>
            </div>
            <Typography id={descriptionId} variant="body" className={styles.muted}>{t(`${file.id}.description`)}</Typography>

            {/* Always in the page, empty or not: a status region inserted with its text already in is often not announced. */}
            <div role="status" className={styles.live}>
                {isGenerating && (
                    <Banner variant="info" title={t('generating.title')} iconStart={<Loader size="small" aria-hidden="true"/>}>
                        {t(file.full ? 'generating.full' : 'generating.index')}
                    </Banner>
                )}
                {!isGenerating && generation && (
                    <Banner variant={generation.failedPages.length || generation.truncated ? 'warning' : 'info'}
                            title={t('generated.title', {pages: generation.pages, language: generation.language})}
                            data-llms-role="generated"
                    >
                        <span>{t('generated.review')}</span>
                        {generation.truncated && <span>{t('generated.truncated')}</span>}
                        {generation.failedPages.length > 0 && (
                            <span>
                                {t('generated.failed', {count: generation.failedPages.length})}
                                <ul className={styles.failed}>
                                    {generation.failedPages.map(url => <li key={url}>{url}</li>)}
                                </ul>
                            </span>
                        )}
                    </Banner>
                )}
            </div>

            <label htmlFor={textareaId} className={styles.srOnly}>{t('editorLabel', {file: file.fileName})}</label>
            <Textarea isResizable
                      id={textareaId}
                      className={styles.editor}
                      value={value}
                      rows={file.full ? 28 : 20}
                      spellCheck={false}
                      aria-describedby={descriptionId}
                      isReadOnly={isGenerating}
                      placeholder={t(`${file.id}.placeholder`)}
                      onChange={e => onChange(e.target.value)}/>
            <Typography variant="caption" className={styles.muted} data-llms-role="stats">
                {t('stats', stats)}
            </Typography>
        </section>
    );
};

FileCard.propTypes = {
    file: PropTypes.shape({
        id: PropTypes.string.isRequired,
        fileName: PropTypes.string.isRequired,
        full: PropTypes.bool.isRequired
    }).isRequired,
    value: PropTypes.string.isRequired,
    saved: PropTypes.string.isRequired,
    isServed: PropTypes.bool.isRequired,
    isCurrent: PropTypes.bool.isRequired,
    generation: PropTypes.shape({
        pages: PropTypes.number,
        failedPages: PropTypes.arrayOf(PropTypes.string),
        truncated: PropTypes.bool,
        language: PropTypes.string
    }),
    isGenerating: PropTypes.bool.isRequired,
    isGenerateEnabled: PropTypes.bool.isRequired,
    liveUrl: PropTypes.string.isRequired,
    onChange: PropTypes.func.isRequired,
    onGenerate: PropTypes.func.isRequired,
    onRevert: PropTypes.func.isRequired
};
