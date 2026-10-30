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
import {LLMS_GENERATE, LLMS_LIVE, LLMS_SAVE, LLMS_SITE} from './Llms.gql';
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
    const live = useQuery(LLMS_LIVE, {variables: {path: sitePath}, fetchPolicy: 'network-only', errorPolicy: 'all'});
    const [saveFiles, {loading: saving}] = useMutation(LLMS_SAVE);

    const node = data?.jcr?.nodeByPath;
    const site = node?.site;
    const saved = useMemo(() => ({
        llms: text(node?.llms),
        llmsFull: text(node?.llmsFull),
        baseUrl: text(node?.baseUrl) || suggestedBaseUrl(site?.serverName)
    }), [node, site]);
    const published = {
        llms: text(live.data?.jcr?.nodeByPath?.llms),
        llmsFull: text(live.data?.jcr?.nodeByPath?.llmsFull)
    };

    const [tab, setTab] = useState('llms');
    const [drafts, setDrafts] = useState({llms: '', llmsFull: ''});
    const [baseUrl, setBaseUrl] = useState('');
    const [language, setLanguage] = useState('');
    const [generations, setGenerations] = useState({});
    const [generating, setGenerating] = useState(null);
    const [failure, setFailure] = useState(null);

    // The drafts follow what is stored each time it is read again (first load, after a save).
    useEffect(() => {
        if (node) {
            setDrafts({llms: saved.llms, llmsFull: saved.llmsFull});
            setBaseUrl(saved.baseUrl);
        }
    }, [node, saved]);
    useEffect(() => {
        if (site && !language) {
            setLanguage(site.defaultLanguage);
        }
    }, [site, language]);

    const baseUrlValid = BASE_URL.test(baseUrl);
    const changed = {
        llms: drafts.llms !== saved.llms,
        llmsFull: drafts.llmsFull !== saved.llmsFull,
        baseUrl: baseUrl !== saved.baseUrl
    };
    const dirty = changed.llms || changed.llmsFull || changed.baseUrl;

    if (loading) {
        return <div className={styles.root}><Loader size="big"/></div>;
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

    const languages = (site?.languages || []).filter(l => l.activeInLive || l.language === site.defaultLanguage)
        .map(l => ({label: `${l.displayName} (${l.language})`, value: l.language}));
    const file = FILES.find(f => f.id === tab);

    const generate = async () => {
        setFailure(null);
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
        }
    };

    const save = async () => {
        setFailure(null);
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
        } catch (e) {
            setFailure({action: 'save', message: errorText(e)});
            toast(t('error.save'), 'error');
        }
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
                                    icon={<Publish/>}
                                    label={t('save')}
                                    isDisabled={!dirty || saving || !baseUrlValid || Boolean(generating)}
                                    onClick={save}/>
                        ]}
                        toolbarLeft={(
                            <div className={styles.tabs}>
                                <Tab className={styles.tabBar}>
                                    {FILES.map(f => (
                                        <TabItem key={f.id}
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
                                    icon={<OpenInNew/>}
                                    label={t('about')}
                                    onClick={() => globalThis.open('https://llmstxt.org/', '_blank', 'noopener')}/>
                        ]}
                    />
                )}
                content={(
                    <div className={styles.content}>
                        {failure && (
                            <Banner variant="danger" title={t(`error.${failure.action}`)} role="alert">
                                {failure.message}
                            </Banner>
                        )}
                        <section className={styles.card} aria-labelledby="llms-settings-title">
                            <Typography id="llms-settings-title" variant="heading" component="h2">{t('settings.title')}</Typography>
                            <Typography variant="body" className={styles.muted}>{t('settings.description')}</Typography>
                            <div className={styles.settings}>
                                <Field id="llms-language-field" label={t('settings.language')} helper={t('settings.languageHelp')}>
                                    <Dropdown data={languages}
                                              value={language}
                                              label={languages.find(l => l.value === language)?.label}
                                              variant="outlined"
                                              onChange={(e, item) => setLanguage(item.value)}/>
                                </Field>
                                <Field id="llms-baseurl-field"
                                       label={t('settings.baseUrl')}
                                       helper={t('settings.baseUrlHelp')}
                                       hasError={!baseUrlValid}
                                       errorMessage={baseUrlValid ? undefined : t('settings.baseUrlError')}>
                                    <Input id="llms-baseurl"
                                           value={baseUrl}
                                           placeholder="https://www.example.com"
                                           onChange={e => setBaseUrl(e.target.value.trim())}/>
                                </Field>
                            </div>
                        </section>
                        <FileCard
                            file={file}
                            value={drafts[file.id]}
                            saved={saved[file.id]}
                            published={live.error ? '' : published[file.id]}
                            generation={generations[file.id]}
                            isGenerating={generating === file.id}
                            canGenerate={baseUrlValid && Boolean(language) && !generating && !saving}
                            liveUrl={fileUrl(file.fileName)}
                            onChange={value => setDrafts(d => ({...d, [file.id]: value}))}
                            onGenerate={generate}
                            onRevert={() => {
                                setDrafts(d => ({...d, [file.id]: saved[file.id]}));
                                setGenerations(g => ({...g, [file.id]: undefined}));
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
