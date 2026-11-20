import React, {useDeferredValue, useMemo} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Button, Chip, OpenInNew, Paper, Textarea, Typography, Undo} from '@jahia/moonstone';
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

// A path is written as it is: nothing here is inserted as HTML.
const PLAIN = {interpolation: {escapeValue: false}};

/**
 * One file: where it stands, where visitors get it, and its text. Generating it is the block above
 * ({@link GenerationCard}); saving it is the action of the page header.
 */
export const FileCard = ({file, value, saved, isServed, isCurrent, isGenerating, liveUrl, rootUrl, onChange, onRevert}) => {
    const {t} = useTranslation(MODULE);
    const state = status(value, saved, isCurrent);
    // Deferred: while the editor types in a large file, React keeps the keystroke ahead of the counts.
    const counted = useDeferredValue(value);
    const stats = useMemo(() => statsOf(counted), [counted]);
    const textareaId = `llms-editor-${file.id}`;
    const descriptionId = `llms-file-description-${file.id}`;

    return (
        <Paper hasPadding className={styles.block}>
            <div className={styles.cardHead}>
                <div className={styles.cardTitle}>
                    <Typography variant="heading" component="h2">{file.fileName}</Typography>
                    <Chip label={t(`status.${state.key}`)} color={state.color} data-llms-status={state.key}/>
                </div>
                <div className={styles.actions}>
                    <Button data-llms-role="revert"
                            variant="ghost"
                            icon={<Undo aria-hidden="true"/>}
                            label={t('revert')}
                            isDisabled={value === saved || isGenerating}
                            onClick={onRevert}/>
                </div>
            </div>
            <Typography id={descriptionId} className={`${styles.muted} ${styles.prose}`}>{t(`${file.id}.description`)}</Typography>
            {/* What visitors get, said in words: the address when the file is served, and that it is not when it is not. */}
            <Typography data-llms-role="served" className={styles.served}>
                {isServed ? (
                    <>
                        {t('servedAt')}{' '}
                        <a className={styles.link} data-llms-role="view" href={liveUrl} target="_blank" rel="noopener noreferrer">
                            {liveUrl}
                            <span className={styles.srOnly}> ({t('view')}, {t('newTab')})</span>
                            <OpenInNew aria-hidden="true" size="small"/>
                        </a>
                        {/* The file of the site is at the root of its domain too; the file of a section is not. */}
                        {rootUrl ? t('servedRoot', {path: rootUrl, ...PLAIN}) : '.'}
                    </>
                ) : t('notServed', {path: liveUrl, ...PLAIN})}
            </Typography>

            <label htmlFor={textareaId} className={styles.srOnly}>{t('editorLabel', {file: file.fileName, ...PLAIN})}</label>
            {/* Busy here only: on an element that held the status region, it could silence its announcements. */}
            <div aria-busy={isGenerating}>
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
            </div>
            <Typography variant="caption" className={styles.muted} data-llms-role="stats">
                {t('stats', stats)}
            </Typography>
        </Paper>
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
    isGenerating: PropTypes.bool.isRequired,
    liveUrl: PropTypes.string.isRequired,
    rootUrl: PropTypes.string,
    onChange: PropTypes.func.isRequired,
    onRevert: PropTypes.func.isRequired
};
