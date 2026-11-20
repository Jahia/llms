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
import {LLMS_GENERATE, LLMS_PROGRESS, LLMS_RULES, LLMS_SAVE, LLMS_SITE, LLMS_START, LLMS_STATUS} from './Llms.gql';
import {FileCard} from './FileCard';
import {GenerationCard} from './GenerationCard';
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

/** One tab per file. A file with unsaved changes says so in its name, not only with a mark. */
const FileTabs = ({selected, changed, onSelect}) => {
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
            </Tab>
        </div>
    );
};

FileTabs.propTypes = {
    selected: PropTypes.string.isRequired,
    changed: PropTypes.objectOf(PropTypes.bool).isRequired,
    onSelect: PropTypes.func.isRequired
};

export const LlmsEditor = ({siteKey}) => {
    const {t} = useTranslation(MODULE);
    const client = useApolloClient();
    const sitePath = `/sites/${siteKey}`;
    const {data, loading, error, refetch} = useQuery(LLMS_SITE, {variables: {path: sitePath}, fetchPolicy: 'network-only'});
    const live = useQuery(LLMS_STATUS, {variables: {siteKey}, fetchPolicy: 'network-only', errorPolicy: 'all'});
    // Read on its own: the panel works without it, it only says less about what is left out.
    const rules = useQuery(LLMS_RULES, {variables: {siteKey}, errorPolicy: 'all'});
    const [saveFiles, {loading: saving}] = useMutation(LLMS_SAVE);

    const node = data?.jcr?.nodeByPath;
    const site = node?.site;
    const saved = useMemo(() => ({
        llms: text(node?.llms),
        llmsFull: text(node?.llmsFull),
        baseUrl: text(node?.baseUrl) || suggestedBaseUrl(site?.serverName)
    }), [node, site]);
    // Nothing is claimed about live when its status could not be read.
    const published = (live.error ? null : live.data?.llms?.status) || {};

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
    const [refocus, setRefocus] = useState(0);

    // The drafts follow what is stored each time it is read again (first load, after a save).
    useEffect(() => {
        if (node) {
            setDrafts({llms: saved.llms, llmsFull: saved.llmsFull});
            setBaseUrl(saved.baseUrl);
        }
    }, [node, saved]);
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
        baseUrl: baseUrl !== saved.baseUrl
    };
    const dirty = changed.llms || changed.llmsFull || changed.baseUrl;

    if (loading) {
        return <div className={styles.root}><Loader size="big" aria-label={t('loading')}/></div>;
    }

    const title = t('header', {siteName: node?.displayName || siteKey});
    if (error || !node) {
        return <ReadFailure title={title} message={errorText(error)}/>;
    }

    // Only the languages visitors are served: the server refuses the others.
    const languages = (site?.languages || []).filter(l => l.activeInLive)
        .map(l => ({label: `${l.displayName} (${l.language})`, value: l.language}));
    const file = FILES.find(f => f.id === tab);

    // The index is read in one request. llms-full.txt renders every page: it runs in the background
    // and the panel follows it, so the editor sees the pages go by.
    const run = async () => {
        if (!file.full) {
            const {data: result} = await client.query({
                query: LLMS_GENERATE,
                variables: {siteKey, language, baseUrl, full: false},
                fetchPolicy: 'network-only'
            });
            return result.llms.generate;
        }

        setProgress(null);
        const {data: started} = await client.mutate({mutation: LLMS_START, variables: {siteKey, language, baseUrl}});
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
                    llms: changed.llms ? drafts.llms : null,
                    llmsFull: changed.llmsFull ? drafts.llmsFull : null,
                    baseUrl: baseUrlValid ? baseUrl : null
                }
            });
            setGenerations({});
            setProgress(null);
            await Promise.all([refetch(), live.refetch()]);
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
    const fileUrl = name => `${globalThis.contextJsParameters?.contextPath || ''}/sites/${siteKey}/${name}`;

    return (
        <div className={styles.root}>
            <LayoutContent
                hasPadding
                data-llms-role="panel"
                header={(
                    <Header
                        title={title}
                        mainActions={[
                            <Button key="save"
                                    data-llms-role="save"
                                    size="big"
                                    color="accent"
                                    icon={<Publish aria-hidden="true"/>}
                                    label={t('save')}
                                    isDisabled={!dirty || saving || !baseUrlValid || Boolean(generating)}
                                    isLoading={saving}
                                    onClick={save}/>
                        ]}
                        toolbarLeft={<FileTabs selected={tab} changed={changed} onSelect={setTab}/>}
                        toolbarRight={[
                            <Button key="spec"
                                    variant="ghost"
                                    icon={<OpenInNew aria-hidden="true"/>}
                                    label={t('about')}
                                    aria-label={`${t('about')} (${t('newTab')})`}
                                    onClick={() => globalThis.open('https://llmstxt.org/', '_blank', 'noopener')}/>
                        ]}
                    />
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
                        {/* One panel per tab: what generates the file, then the file. */}
                        <div role="tabpanel"
                             id="llms-tabpanel"
                             aria-labelledby={`llms-tab-${file.id}`}
                             className={styles.panel}
                             data-llms-file={file.id}
                        >
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
                                isGenerating={generating === file.id}
                                isGenerateEnabled={baseUrlValid && Boolean(language) && !saving && (!generating || generating === file.id)}
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
                    </div>
                )}
            />
        </div>
    );
};

LlmsEditor.propTypes = {
    siteKey: PropTypes.string.isRequired
};
