import React from 'react';
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

/** Where the file stands, from what is typed, stored and served. */
const status = (value, saved, published) => {
    if (value !== saved) {
        return {key: 'unsaved', color: 'accent'};
    }
    if (!saved.trim()) {
        return {key: 'none', color: 'default'};
    }
    if (saved.trim() === PLACEHOLDER) {
        return {key: 'placeholder', color: 'warning'};
    }
    return saved === published ? {key: 'published', color: 'success'} : {key: 'notPublished', color: 'warning'};
};

export const FileCard = ({file, value, saved, published, generation, isGenerating, canGenerate, liveUrl, onChange, onGenerate, onRevert}) => {
    const {t} = useTranslation(MODULE);
    const state = status(value, saved, published);
    const textareaId = `llms-editor-${file.id}`;
    const titleId = `llms-file-title-${file.id}`;

    return (
        <section className={styles.card} aria-labelledby={titleId} data-llms-file={file.id}>
            <div className={styles.cardHead}>
                <div className={styles.cardTitle}>
                    <Typography id={titleId} variant="heading" component="h2">{file.fileName}</Typography>
                    <Chip label={t(`status.${state.key}`)} color={state.color} data-llms-status={state.key}/>
                </div>
                <div className={styles.actions}>
                    <Button data-llms-role="generate"
                            icon={<Reload/>}
                            label={t(saved.trim() ? 'regenerate' : 'generate')}
                            isDisabled={!canGenerate}
                            onClick={onGenerate}/>
                    <Button data-llms-role="revert"
                            variant="ghost"
                            icon={<Undo/>}
                            label={t('revert')}
                            isDisabled={value === saved || isGenerating}
                            onClick={onRevert}/>
                    <Button data-llms-role="view"
                            variant="ghost"
                            icon={<OpenInNew/>}
                            label={t('view')}
                            isDisabled={!published.trim()}
                            onClick={() => globalThis.open(liveUrl, '_blank', 'noopener')}/>
                </div>
            </div>
            <Typography variant="body" className={styles.muted}>{t(`${file.id}.description`)}</Typography>

            {isGenerating && (
                <Banner variant="info" title={t('generating.title')} iconStart={<Loader size="small"/>} role="status">
                    {t(file.full ? 'generating.full' : 'generating.index')}
                </Banner>
            )}
            {!isGenerating && generation && (
                <Banner variant={generation.failedPages.length || generation.truncated ? 'warning' : 'info'}
                        title={t('generated.title', {pages: generation.pages, language: generation.language})}
                        role="status"
                        data-llms-role="generated">
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

            <label htmlFor={textareaId} className={styles.srOnly}>{t('editorLabel', {file: file.fileName})}</label>
            <Textarea id={textareaId}
                      className={styles.editor}
                      value={value}
                      rows={file.full ? 28 : 20}
                      spellCheck={false}
                      isResizable
                      isReadOnly={isGenerating}
                      placeholder={t(`${file.id}.placeholder`)}
                      onChange={e => onChange(e.target.value)}/>
            <Typography variant="caption" className={styles.muted} data-llms-role="stats">
                {t('stats', {
                    lines: value ? value.split('\n').length : 0,
                    links: (value.match(LINK) || []).length,
                    size: size(value)
                })}
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
    published: PropTypes.string.isRequired,
    generation: PropTypes.shape({
        pages: PropTypes.number,
        failedPages: PropTypes.arrayOf(PropTypes.string),
        truncated: PropTypes.bool,
        language: PropTypes.string
    }),
    isGenerating: PropTypes.bool.isRequired,
    canGenerate: PropTypes.bool.isRequired,
    liveUrl: PropTypes.string.isRequired,
    onChange: PropTypes.func.isRequired,
    onGenerate: PropTypes.func.isRequired,
    onRevert: PropTypes.func.isRequired
};
