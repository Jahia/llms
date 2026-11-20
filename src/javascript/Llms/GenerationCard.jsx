import React from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Banner, Button, Dropdown, Input, Paper, Reload, Typography} from '@jahia/moonstone';
import {GenerationProgress} from './GenerationProgress';
import {GenerationRules} from './GenerationRules';
import {MODULE} from './module';
import styles from './Llms.module.css';

// File names and paths are written as they are: nothing here is inserted as HTML.
const PLAIN = {interpolation: {escapeValue: false}};

// Before the server has listed the pages: the site is being read, nothing is counted yet.
const STARTING = {isStarted: false, total: 0, done: 0, pages: []};

/**
 * What generation needs and the button that runs it, in one block: the language and the public URL sit
 * next to the action they feed. The two values are shared by both files; the button names the file of
 * the open tab.
 */
export const GenerationCard = ({
    file, languages, language, baseUrl, generation, progress, rules, failure,
    isBaseUrlValid, isBaseUrlLocked, isGenerating, isGenerateEnabled, hasSavedFile,
    onLanguageChange, onBaseUrlChange, onGenerate
}) => {
    const {t} = useTranslation(MODULE);

    return (
        <Paper hasPadding className={styles.block}>
            <Typography variant="heading" component="h2">{t('settings.title')}</Typography>
            <Typography id="llms-generation-description" className={`${styles.muted} ${styles.prose}`}>
                {t('settings.description')}
            </Typography>
            <GenerationRules rules={rules}/>
            <div className={styles.settings}>
                <div className={styles.field}>
                    <Typography id="llms-language-label" weight="bold">{t('settings.language')}</Typography>
                    <Typography id="llms-language-help" variant="caption" className={styles.muted}>
                        {t('settings.languageHelp')}
                    </Typography>
                    {/* The Dropdown names itself after its value: the group around it carries the field's name. */}
                    <Dropdown data={languages}
                              value={language}
                              variant="outlined"
                              role="group"
                              aria-label={t('settings.language')}
                              aria-describedby="llms-language-help"
                              onChange={(e, item) => onLanguageChange(item.value)}/>
                </div>
                <div className={styles.field}>
                    <Typography component="label" htmlFor="llms-baseurl" weight="bold">{t('settings.baseUrl')}</Typography>
                    <Typography id="llms-baseurl-help" variant="caption" className={styles.muted}>
                        {t(isBaseUrlLocked ? 'settings.baseUrlSection' : 'settings.baseUrlHelp')}
                    </Typography>
                    <Input id="llms-baseurl"
                           size="big"
                           variant="outlined"
                           value={baseUrl}
                           placeholder="https://www.example.com"
                           autoComplete="url"
                           inputMode="url"
                           spellCheck={false}
                           isReadOnly={isBaseUrlLocked}
                           aria-invalid={!isBaseUrlValid}
                           aria-describedby={isBaseUrlValid ? 'llms-baseurl-help' : 'llms-baseurl-help llms-baseurl-error'}
                           onChange={e => onBaseUrlChange(e.target.value.trim())}/>
                    {!isBaseUrlValid && (
                        <Typography id="llms-baseurl-error" variant="caption" className={styles.error}>
                            {t('settings.baseUrlError')}
                        </Typography>
                    )}
                </div>
            </div>
            <div className={styles.actions}>
                {/* While a file is generated the button stays focusable: disabling it would drop the focus. */}
                <Button data-llms-role="generate"
                        icon={<Reload aria-hidden="true"/>}
                        label={t(hasSavedFile ? 'regenerateFile' : 'generateFile', {file: file.fileName, ...PLAIN})}
                        isDisabled={!isGenerateEnabled}
                        aria-disabled={isGenerating ? 'true' : undefined}
                        aria-describedby="llms-generation-description"
                        onClick={() => {
                            if (!isGenerating) {
                                onGenerate();
                            }
                        }}/>
            </div>
            {failure && (
                <Banner variant="danger" title={t('error.generate')} role="alert" data-llms-role="generate-error">
                    {failure}
                </Banner>
            )}
            {/* Always in the page, empty or not: a status region inserted with its text already in is often not announced. */}
            <div role="status" className={styles.live}>
                {isGenerating && !file.full && (
                    <Banner variant="info" title={t('generating.title')} data-llms-role="generating">
                        {t('generating.index')}
                    </Banner>
                )}
                {!isGenerating && generation && (
                    <Banner variant={generation.failedPages.length > 0 || generation.truncated ? 'warning' : 'info'}
                            title={t('generated.title', {pages: generation.pages, language: generation.language})}
                            data-llms-role="generated"
                    >
                        <span>{t('generated.review')}</span>
                        {generation.truncated && (
                            <span data-llms-role="cut">{t(`generated.cut.${generation.cutBy || 'pages'}`, {count: generation.skipped})}</span>
                        )}
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
            {/* llms-full.txt renders every page: while it runs this block says so and shows the pages go by, and
                once it is over the pages stay listed under the result. One element at one place for both, so
                the reader in the list keeps their place when the generation ends. Outside the status region
                above: read every second, it would be announced every second. */}
            {file.full && (isGenerating || progress) && (
                <GenerationProgress isRunning={isGenerating} fileName={file.fileName} progress={progress || STARTING}/>
            )}
        </Paper>
    );
};

GenerationCard.propTypes = {
    file: PropTypes.shape({
        fileName: PropTypes.string.isRequired,
        full: PropTypes.bool.isRequired
    }).isRequired,
    languages: PropTypes.arrayOf(PropTypes.shape({
        label: PropTypes.string.isRequired,
        value: PropTypes.string.isRequired
    })).isRequired,
    language: PropTypes.string.isRequired,
    baseUrl: PropTypes.string.isRequired,
    generation: PropTypes.shape({
        pages: PropTypes.number,
        failedPages: PropTypes.arrayOf(PropTypes.string),
        truncated: PropTypes.bool,
        cutBy: PropTypes.string,
        skipped: PropTypes.number,
        language: PropTypes.string
    }),
    progress: PropTypes.shape({
        runId: PropTypes.string.isRequired
    }),
    rules: PropTypes.object,
    failure: PropTypes.string,
    isBaseUrlValid: PropTypes.bool.isRequired,
    isBaseUrlLocked: PropTypes.bool.isRequired,
    isGenerating: PropTypes.bool.isRequired,
    isGenerateEnabled: PropTypes.bool.isRequired,
    hasSavedFile: PropTypes.bool.isRequired,
    onLanguageChange: PropTypes.func.isRequired,
    onBaseUrlChange: PropTypes.func.isRequired,
    onGenerate: PropTypes.func.isRequired
};
