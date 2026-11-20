import React, {useEffect, useMemo, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useApolloClient, useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {
    Banner,
    Button,
    Header,
    LayoutContent,
    Loader,
    OpenInNew,
    Publish,
    Tab,
    TabItem
} from '@jahia/moonstone';
import {LLMS_GENERATE, LLMS_PROGRESS, LLMS_RULES, LLMS_SAVE, LLMS_SETTINGS, LLMS_SITE, LLMS_START, LLMS_STATUS} from './Llms.gql';
import {FileCard} from './FileCard';
import {GenerationCard} from './GenerationCard';
import {ScopeSelector} from './ScopeSelector';
import {SiteSettingsPanel} from './SiteSettingsPanel';
import {useScope} from './useScope';
import {BASE_URL, FILES, MODULE} from './module';
import styles from './Llms.module.css';

const text = property => property?.value ?? '';

/** Where links point when nothing is saved: the site's own server name, else the address jContent runs on. */
const suggestedBaseUrl = serverName => (serverName && serverName !== 'localhost' ?
    `https://${serverName}` :
    globalThis.location.origin);

const toast = (message, variant) => globalThis.jahia?.toastDispatcher?.add({message, variant});

// How often the panel asks how far a generation of llms-full.txt is.
const POLL_MS = 800;

const wait = ms => new Promise(resolve => {
    setTimeout(resolve, ms);
});

/** What went wrong in the given action, or undefined when the last failure is another action's or none. */
const messageOf = (failure, action, fallback) => (failure && failure.action === action ? failure.message || fallback : undefined);

/** What the server says of the files in live; nothing is claimed when it could not be read. */
const statusOf = live => (live.error ? null : live.data?.llms?.status) || {};

/** Generate needs a valid public URL and a language, and waits for a save or for the other file to end. */
const canGenerate = ({baseUrlValid, language, saving, generating, fileId}) =>
    baseUrlValid && Boolean(language) && !saving && (!generating || generating === fileId);

const contextPath = () => globalThis.contextJsParameters?.contextPath || '';

const errorText = error => error?.graphQLErrors?.[0]?.message || error?.message || '';

/** Asks the server how far a generation of llms-full.txt is until it is over, and returns the file. */
const followGeneration = async ({client, siteKey, id, isMounted, onProgress}) => {
    const {data: result} = await client.query({
        query: LLMS_PROGRESS,
        variables: {siteKey, id},
        fetchPolicy: 'no-cache'
    });
    const state = result.llms.generationProgress;
    if (!isMounted.current) {
        throw new Error('');
    }

    onProgress({runId: id, isStarted: state.started, total: state.total, done: state.done, pages: state.pages});
    if (state.finished) {
        if (state.error || !state.generation) {
            throw new Error(state.error || '');
        }

        return state.generation;
    }

    await wait(POLL_MS);
    return followGeneration({client, siteKey, id, isMounted, onProgress});
};

/** The panel when the settings of the site could not be read: its title, and what went wrong. */
const ReadFailure = ({title, message}) => {
    const {t} = useTranslation(MODULE);
    return (
        <div className={styles.root}>
            <LayoutContent
                hasPadding
                header={<Header title={title}/>}
                content={(
                    <div className={styles.content}>
                        <Banner variant="danger" title={t('error.read')} role="alert">{message}</Banner>
                    </div>
                )}
            />
        </div>
    );
};

ReadFailure.propTypes = {
    title: PropTypes.string.isRequired,
    message: PropTypes.string.isRequired
};

/** What the panel shows in place of nothing while the files of a section load, or when they cannot be read. */
const ScopeState = ({isLoading, error}) => {
    const {t} = useTranslation(MODULE);
    if (isLoading) {
        return <Loader size="big" aria-label={t('loading')}/>;
    }

    return error ? <Banner variant="danger" title={t('error.read')} role="alert">{errorText(error)}</Banner> : null;
};

ScopeState.propTypes = {
    isLoading: PropTypes.bool.isRequired,
    error: PropTypes.object
};

// The tab of what the site lists, beside the tabs of the files.
const SETTINGS_TAB = 'settings';
// The configuration reaches the module a moment after it is written: the rules are read again then.
const SETTINGS_APPLY_MS = 2500;

/**
 * One tab per file, and one for the settings of the site for who may change them. A file with unsaved
 * changes says so in its name, not only with a mark.
 */
const FileTabs = ({selected, changed, hasSettings, onSelect}) => {
    const {t} = useTranslation(MODULE);
    return (
        <div className={styles.tabs}>
            <Tab className={styles.tabBar} aria-label={t('tabsLabel')}>
                {FILES.map(f => {
                    const unsaved = changed[f.id] ? t('tabUnsaved', {file: f.fileName}) : undefined;
                    return (
                        <TabItem key={f.id}
                                 id={`llms-tab-${f.id}`}
                                 aria-controls="llms-tabpanel"
                                 aria-label={unsaved}
                                 title={unsaved}
                                 data-llms-tab={f.id}
                                 label={unsaved ? `${f.fileName} *` : f.fileName}
                                 isSelected={selected === f.id}
                                 onClick={() => onSelect(f.id)}/>
                    );
                })}
                {hasSettings && (
                    <TabItem id={`llms-tab-${SETTINGS_TAB}`}
                             aria-controls="llms-tabpanel"
                             data-llms-tab={SETTINGS_TAB}
                             label={t('config.tab')}
                             isSelected={selected === SETTINGS_TAB}
                             onClick={() => onSelect(SETTINGS_TAB)}/>
                )}
            </Tab>
        </div>
    );
};

FileTabs.propTypes = {
    selected: PropTypes.string.isRequired,
    changed: PropTypes.objectOf(PropTypes.bool).isRequired,
    hasSettings: PropTypes.bool.isRequired,
    onSelect: PropTypes.func.isRequired
};

/** The header of the panel: its title, Save and publish, one tab per file, and the link to the proposal. */
const EditorHeader = ({title, tab, changed, hasSettings, isSaving, isSaveBlocked, onSave, onSelectTab}) => {
    const {t} = useTranslation(MODULE);
    return (
        <Header
            title={title}
            // Save and publish is for the files: the settings have their own Save, in their form.
            mainActions={tab === SETTINGS_TAB ? [] : [
                <Button key="save"
                        data-llms-role="save"
                        size="big"
                        color="accent"
                        icon={<Publish aria-hidden="true"/>}
                        label={t('save')}
                        isDisabled={isSaveBlocked || isSaving}
                        isLoading={isSaving}
                        onClick={onSave}/>
            ]}
            toolbarLeft={<FileTabs selected={tab} changed={changed} hasSettings={hasSettings} onSelect={onSelectTab}/>}
            toolbarRight={[
                <Button key="spec"
                        variant="ghost"
                        icon={<OpenInNew aria-hidden="true"/>}
                        label={t('about')}
                        aria-label={`${t('about')} (${t('newTab')})`}
                        onClick={() => globalThis.open('https://llmstxt.org/', '_blank', 'noopener')}/>
            ]}
        />
    );
};

EditorHeader.propTypes = {
    title: PropTypes.string.isRequired,
    tab: PropTypes.string.isRequired,
    changed: PropTypes.objectOf(PropTypes.bool).isRequired,
    hasSettings: PropTypes.bool.isRequired,
    isSaving: PropTypes.bool.isRequired,
    isSaveBlocked: PropTypes.bool.isRequired,
    onSave: PropTypes.func.isRequired,
    onSelectTab: PropTypes.func.isRequired
};

/** The values the site may set. Answered only to who may change them: null for the others, who get no Settings tab. */
const useSiteSettings = siteKey => {
    const {data, error} = useQuery(LLMS_SETTINGS, {variables: {siteKey}, fetchPolicy: 'network-only', errorPolicy: 'all'});
    return (!error && data?.llms?.settings) || null;
};

/** The file of a tab. The settings are no file: what the files need then reads the first one, and is not shown. */
const fileOf = tab => FILES.find(f => f.id === tab) || FILES[0];

export const LlmsEditor = ({siteKey}) => {
    const {t} = useTranslation(MODULE);
    const client = useApolloClient();
    const sitePath = `/sites/${siteKey}`;
    const {data, loading, error, refetch} = useQuery(LLMS_SITE, {variables: {path: sitePath}, fetchPolicy: 'network-only'});
    // The whole site, or one section of it: the files then come from the page of the section.
    const scoped = useScope(siteKey);
    const live = useQuery(LLMS_STATUS, {variables: {siteKey, path: scoped.scope}, fetchPolicy: 'network-only', errorPolicy: 'all'});
    // Read on its own: the panel works without it, it only says less about what is left out.
    const rules = useQuery(LLMS_RULES, {variables: {siteKey}, errorPolicy: 'all'});
    const settingFields = useSiteSettings(siteKey);
    const [saveFiles, {loading: saving}] = useMutation(LLMS_SAVE);

    const node = data?.jcr?.nodeByPath;
    const site = node?.site;
    const fileNode = scoped.scope ? scoped.node : node;
    const saved = useMemo(() => ({
        llms: text(fileNode?.llms),
        llmsFull: text(fileNode?.llmsFull),
        // The public URL is the site's, whatever the scope.
        baseUrl: text(node?.baseUrl) || suggestedBaseUrl(site?.serverName)
    }), [fileNode, node, site]);
    const published = statusOf(live);

    const [tab, setTab] = useState('llms');
    const [drafts, setDrafts] = useState({llms: '', llmsFull: ''});
    const [baseUrl, setBaseUrl] = useState('');
    const [language, setLanguage] = useState('');
    const [generations, setGenerations] = useState({});
    const [generating, setGenerating] = useState(null);
    // The generation of llms-full.txt as it goes, kept after it ends: {runId, isStarted, total, done, pages}.
    const [progress, setProgress] = useState(null);
    const isMounted = useRef(true);
    useEffect(() => () => {
        isMounted.current = false;
    }, []);
    const [failure, setFailure] = useState(null);
    const [announcement, setAnnouncement] = useState('');
    const [refusal, setRefusal] = useState('');
    // The scope just chosen, to be announced once its files are there.
    const chosen = useRef(null);
    const [refocus, setRefocus] = useState(0);

    // The drafts follow what is stored each time it is read again (first load, after a save).
    useEffect(() => {
        if (fileNode) {
            setDrafts({llms: saved.llms, llmsFull: saved.llmsFull});
            setBaseUrl(saved.baseUrl);
        }
    }, [fileNode, saved]);
    // Said once the files of the scope are shown, not when it is chosen: the title changes without a word.
    useEffect(() => {
        if (chosen.current && !scoped.isLoading) {
            setAnnouncement(t('scope.showing', {name: chosen.current}));
            chosen.current = null;
        }
    }, [scoped.isLoading, scoped.scope, t]);
    useEffect(() => {
        if (site && !language) {
            const served = (site.languages || []).filter(l => l.activeInLive).map(l => l.language);
            setLanguage(served.includes(site.defaultLanguage) ? site.defaultLanguage : served[0] || '');
        }
    }, [site, language]);

    // Discard changes and Save disable themselves once used, and a disabled button drops the
    // focus: after each of them, a focus left nowhere goes to the editor of the file.
    useEffect(() => {
        if (refocus > 0) {
            const active = document.activeElement;
            const editor = document.getElementById(`llms-editor-${tab}`);
            if (editor && (!active || active === document.body || active.disabled)) {
                editor.focus();
            }
        }
        // Runs after an action only, not when the tab changes: the focus then stays on the tab.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [refocus]);

    const baseUrlValid = BASE_URL.test(baseUrl);
    const changed = {
        llms: drafts.llms !== saved.llms,
        llmsFull: drafts.llmsFull !== saved.llmsFull,
        // Saved with the files of the site only.
        baseUrl: !scoped.scope && baseUrl !== saved.baseUrl
    };
    const dirty = changed.llms || changed.llmsFull || changed.baseUrl;

    if (loading) {
        return <div className={styles.root}><Loader size="big" aria-label={t('loading')}/></div>;
    }

    const siteName = node?.displayName || siteKey;
    const scopeName = scoped.titleOf(scoped.scope) || siteName;
    const title = t('header', {siteName: scopeName});
    if (error || !node) {
        return <ReadFailure title={title} message={errorText(error)}/>;
    }

    // Only the languages visitors are served: the server refuses the others.
    const languages = (site?.languages || []).filter(l => l.activeInLive)
        .map(l => ({label: `${l.displayName} (${l.language})`, value: l.language}));
    const isSettings = tab === SETTINGS_TAB && settingFields !== null;
    const file = fileOf(tab);

    // The index is read in one request. llms-full.txt renders every page: it runs in the background
    // and the panel follows it, so the editor sees the pages go by.
    const run = async () => {
        if (!file.full) {
            const {data: result} = await client.query({
                query: LLMS_GENERATE,
                variables: {siteKey, path: scoped.scope, language, baseUrl, full: false},
                fetchPolicy: 'network-only'
            });
            return result.llms.generate;
        }

        setProgress(null);
        const {data: started} = await client.mutate({mutation: LLMS_START, variables: {siteKey, path: scoped.scope, language, baseUrl}});
        return followGeneration({client, siteKey, id: started.llms.startGeneration, isMounted, onProgress: setProgress});
    };

    const generate = async () => {
        setFailure(null);
        setAnnouncement('');
        setGenerating(file.id);
        try {
            const generation = await run();
            setDrafts(d => ({...d, [file.id]: generation.content}));
            const languageLabel = languages.find(l => l.value === language)?.label || language;
            setGenerations(g => ({...g, [file.id]: {...generation, language: languageLabel}}));
        } catch (e) {
            setFailure({action: 'generate', message: errorText(e)});
        } finally {
            // The focus stays on Generate, which is never disabled while it runs: the result shows below it.
            setGenerating(null);
        }
    };

    const save = async () => {
        setFailure(null);
        setAnnouncement('');
        try {
            await saveFiles({
                variables: {
                    siteKey,
                    path: scoped.scope,
                    llms: changed.llms ? drafts.llms : null,
                    llmsFull: changed.llmsFull ? drafts.llmsFull : null,
                    baseUrl: baseUrlValid ? baseUrl : null
                }
            });
            setGenerations({});
            setProgress(null);
            await Promise.all([refetch(), live.refetch(), scoped.refetch()]);
            toast(t('notification.saved'), 'success');
            // The toast belongs to jContent and may not be announced: the status region says it too.
            setAnnouncement(t('notification.saved'));
        } catch (e) {
            // The alert banner reports the failure: a toast on top of it would be announced twice.
            setFailure({action: 'save', message: errorText(e)});
        }

        setRefocus(n => n + 1);
    };

    const saveFailure = messageOf(failure, 'save', '');
    const fileUrl = name => `${contextPath()}${scoped.scope || sitePath}/${name}`;

    // A scope chosen with unsaved changes, or while a file is generated, is refused: nothing changes.
    const selectScope = path => {
        setRefusal('');
        if (path === scoped.scope) {
            return;
        }

        if (dirty || generating) {
            // Emptied first, written next: a second refusal is announced like the first.
            setTimeout(() => setRefusal(t('scope.refused')), 0);
            return;
        }

        chosen.current = scoped.titleOf(path) || siteName;
        setGenerations({});
        setProgress(null);
        setFailure(null);
        scoped.setScope(path);
    };

    return (
        <div className={styles.root}>
            <LayoutContent
                hasPadding
                data-llms-role="panel"
                header={(
                    <EditorHeader title={title}
                                  tab={tab}
                                  changed={changed}
                                  hasSettings={settingFields !== null}
                                  isSaving={saving}
                                  isSaveBlocked={!dirty || !baseUrlValid || Boolean(generating)}
                                  onSave={save}
                                  onSelectTab={setTab}/>
                )}
                content={(
                    <div className={styles.content}>
                        {/* Always in the page: a status region inserted with its text already in is often not announced. */}
                        <div role="status" className={styles.srOnly}>{announcement}</div>
                        {saveFailure !== undefined && (
                            <Banner variant="danger" title={t('error.save')} role="alert">
                                {saveFailure}
                            </Banner>
                        )}
                        {/* Before the panel of the tabs: the scope applies to both files. Only when there is a choice. */}
                        {/* The settings are the site's, whatever the section: no choice of section above them. */}
                        {isSettings ? (
                            <div role="tabpanel" id="llms-tabpanel" aria-labelledby={`llms-tab-${SETTINGS_TAB}`} className={styles.panel}>
                                <SiteSettingsPanel siteKey={siteKey}
                                                   fields={settingFields}
                                                   onAnnounce={setAnnouncement}
                                                   onSaved={() => setTimeout(() => rules.refetch(), SETTINGS_APPLY_MS)}/>
                            </div>
                        ) : (
                            <>
                                {scoped.sections.length > 0 && (
                                <ScopeSelector siteName={siteName}
                                               sections={scoped.sections}
                                               scope={scoped.scope}
                                               refusal={refusal}
                                               onSelect={selectScope}/>
                        )}
                                {/* One panel per tab: what generates the file, then the file. It stays in place
                            while the files of a section load, so the tabs keep the panel they control. */}
                                <div role="tabpanel"
                                     id="llms-tabpanel"
                                     aria-labelledby={`llms-tab-${file.id}`}
                                     aria-busy={scoped.isLoading}
                                     className={styles.panel}
                                     data-llms-file={file.id}
                                >
                                    <ScopeState isLoading={scoped.isLoading} error={scoped.error}/>
                                    <GenerationCard
                                file={file}
                                languages={languages}
                                language={language}
                                baseUrl={baseUrl}
                                generation={generations[file.id]}
                                progress={file.full ? progress : null}
                                rules={rules.data?.llms?.rules}
                                failure={messageOf(failure, 'generate', t('error.generate'))}
                                isBaseUrlValid={baseUrlValid}
                                isBaseUrlLocked={Boolean(scoped.scope)}
                                isGenerating={generating === file.id}
                                isGenerateEnabled={canGenerate({baseUrlValid, language, saving, generating, fileId: file.id})}
                                hasSavedFile={Boolean(saved[file.id].trim())}
                                onLanguageChange={setLanguage}
                                onBaseUrlChange={setBaseUrl}
                                onGenerate={generate}
                            />
                                    <FileCard
                                file={file}
                                value={drafts[file.id]}
                                saved={saved[file.id]}
                                isServed={Boolean(published[file.id]?.served)}
                                isCurrent={Boolean(published[file.id]?.current)}
                                isGenerating={generating === file.id}
                                liveUrl={fileUrl(file.fileName)}
                                rootUrl={scoped.scope ? null : `/${file.fileName}`}
                                onChange={value => setDrafts(d => ({...d, [file.id]: value}))}
                                onRevert={() => {
                                    setDrafts(d => ({...d, [file.id]: saved[file.id]}));
                                    setGenerations(g => ({...g, [file.id]: undefined}));
                                    if (file.full) {
                                        setProgress(null);
                                    }

                                    setRefocus(n => n + 1);
                                }}
                            />
                                </div>
                            </>
                        )}
                    </div>
                )}
            />
        </div>
    );
};

LlmsEditor.propTypes = {
    siteKey: PropTypes.string.isRequired
};
