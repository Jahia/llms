import React, {useEffect, useMemo, useState} from 'react';
import PropTypes from 'prop-types';
import {useApolloClient, useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {
    Banner,
    Button,
    Dropdown,
    Field,
    Header,
    Input,
    LayoutContent,
    Loader,
    OpenInNew,
    Publish,
    Tab,
    TabItem,
    Typography
} from '@jahia/moonstone';
import {LLMS_GENERATE, LLMS_SAVE, LLMS_SITE, LLMS_STATUS} from './Llms.gql';
import {FileCard} from './FileCard';
import {BASE_URL, FILES, MODULE} from './module';
import styles from './Llms.module.css';

const text = property => property?.value ?? '';

/** Where links point when nothing is saved: the site's own server name, else the address jContent runs on. */
const suggestedBaseUrl = serverName => (serverName && serverName !== 'localhost' ?
    `https://${serverName}` :
    globalThis.location.origin);

const toast = (message, variant) => globalThis.jahia?.toastDispatcher?.add({message, variant});

const errorText = error => error?.graphQLErrors?.[0]?.message || error?.message || '';

export const LlmsEditor = ({siteKey}) => {
    const {t} = useTranslation(MODULE);
    const client = useApolloClient();
    const sitePath = `/sites/${siteKey}`;
    const {data, loading, error, refetch} = useQuery(LLMS_SITE, {variables: {path: sitePath}, fetchPolicy: 'network-only'});
    const live = useQuery(LLMS_STATUS, {variables: {siteKey}, fetchPolicy: 'network-only', errorPolicy: 'all'});
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

    // Generate, Discard changes and Save disable themselves once used, and a disabled button drops the
    // focus: after each of them, a focus left nowhere goes to the editor of the file.
    useEffect(() => {
        if (refocus > 0) {
            const active = document.activeElement;
            if (!active || active === document.body || active.disabled) {
                document.getElementById(`llms-editor-${tab}`)?.focus();
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
        return (
            <div className={styles.root}>
                <LayoutContent
                    hasPadding
                    header={<Header title={title}/>}
                    content={(
                        <div className={styles.content}>
                            <Banner variant="danger" title={t('error.read')} role="alert">{errorText(error)}</Banner>
                        </div>
                    )}
                />
            </div>
        );
    }

    // Only the languages visitors are served: the server refuses the others.
    const languages = (site?.languages || []).filter(l => l.activeInLive)
        .map(l => ({label: `${l.displayName} (${l.language})`, value: l.language}));
    const file = FILES.find(f => f.id === tab);

    const generate = async () => {
        setFailure(null);
        setAnnouncement('');
        setGenerating(file.id);
        try {
            const {data: result} = await client.query({
                query: LLMS_GENERATE,
                variables: {siteKey, language, baseUrl, full: file.full},
                fetchPolicy: 'network-only'
            });
            const generation = result.llms.generate;
            setDrafts(d => ({...d, [file.id]: generation.content}));
            const languageLabel = languages.find(l => l.value === language)?.label || language;
            setGenerations(g => ({...g, [file.id]: {...generation, language: languageLabel}}));
        } catch (e) {
            setFailure({action: 'generate', message: errorText(e)});
        } finally {
            setGenerating(null);
            setRefocus(n => n + 1);
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
                                    onClick={save}/>
                        ]}
                        toolbarLeft={(
                            <div className={styles.tabs}>
                                <Tab className={styles.tabBar} aria-label={t('tabsLabel')}>
                                    {FILES.map(f => (
                                        <TabItem key={f.id}
                                                 id={`llms-tab-${f.id}`}
                                                 aria-controls="llms-tabpanel"
                                                 aria-label={changed[f.id] ? t('tabUnsaved', {file: f.fileName}) : undefined}
                                                 title={changed[f.id] ? t('tabUnsaved', {file: f.fileName}) : undefined}
                                                 data-llms-tab={f.id}
                                                 label={changed[f.id] ? `${f.fileName} *` : f.fileName}
                                                 isSelected={tab === f.id}
                                                 onClick={() => setTab(f.id)}/>
                                    ))}
                                </Tab>
                            </div>
                        )}
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
                        {failure && (
                            <Banner variant="danger" title={t(`error.${failure.action}`)} role="alert">
                                {failure.message}
                            </Banner>
                        )}
                        <section className={styles.card} aria-labelledby="llms-settings-title">
                            <Typography id="llms-settings-title" variant="heading" component="h2">{t('settings.title')}</Typography>
                            <Typography variant="body" className={styles.muted}>{t('settings.description')}</Typography>
                            <div className={styles.settings}>
                                {/* Moonstone's Field does not tie its label, helper or error to the control: done here. */}
                                <Field id="llms-language-field"
                                       label={t('settings.language')}
                                       helper={<span id="llms-language-help">{t('settings.languageHelp')}</span>}>
                                    <Dropdown data={languages}
                                              value={language}
                                              variant="outlined"
                                              role="group"
                                              aria-label={t('settings.language')}
                                              aria-describedby="llms-language-help"
                                              onChange={(e, item) => setLanguage(item.value)}/>
                                </Field>
                                <Field id="llms-baseurl-field"
                                       label={t('settings.baseUrl')}
                                       helper={<span id="llms-baseurl-help">{t('settings.baseUrlHelp')}</span>}
                                       hasError={!baseUrlValid}
                                       errorMessage={baseUrlValid ? undefined : <span id="llms-baseurl-error">{t('settings.baseUrlError')}</span>}>
                                    <Input id="llms-baseurl"
                                           value={baseUrl}
                                           placeholder="https://www.example.com"
                                           autoComplete="url"
                                           inputMode="url"
                                           spellCheck={false}
                                           aria-label={t('settings.baseUrl')}
                                           aria-invalid={!baseUrlValid}
                                           aria-describedby={baseUrlValid ? 'llms-baseurl-help' : 'llms-baseurl-help llms-baseurl-error'}
                                           onChange={e => setBaseUrl(e.target.value.trim())}/>
                                </Field>
                            </div>
                        </section>
                        <FileCard
                            file={file}
                            value={drafts[file.id]}
                            saved={saved[file.id]}
                            isServed={Boolean(published[file.id]?.served)}
                            isCurrent={Boolean(published[file.id]?.current)}
                            generation={generations[file.id]}
                            isGenerating={generating === file.id}
                            canGenerate={baseUrlValid && Boolean(language) && !generating && !saving}
                            liveUrl={fileUrl(file.fileName)}
                            onChange={value => setDrafts(d => ({...d, [file.id]: value}))}
                            onGenerate={generate}
                            onRevert={() => {
                                setDrafts(d => ({...d, [file.id]: saved[file.id]}));
                                setGenerations(g => ({...g, [file.id]: undefined}));
                                setRefocus(n => n + 1);
                            }}
                        />
                    </div>
                )}
            />
        </div>
    );
};

LlmsEditor.propTypes = {
    siteKey: PropTypes.string.isRequired
};
