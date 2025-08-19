import React, {useEffect, useState} from 'react';
import {useMutation, useQuery} from '@apollo/react-hooks';
import {LlmsQuery} from './Llms.gql-queries';
import {LlmsMutation} from './Llms.gql-mutations';
import {Book, Button, Header, Typography} from '@jahia/moonstone';
import styles from './Llms.component.scss';
import {useTranslation} from 'react-i18next';
import {useNotifications} from '@jahia/react-material';

export default () => {
    const sitePath = '/sites/' + window.contextJsParameters.siteKey;
    const {t} = useTranslation('llms');
    const [textArea, setTextArea] = useState('');
    const notificationContext = useNotifications();
    const {data, error, loading, refetch} = useQuery(LlmsQuery, {
        variables: {path: sitePath, language: window.contextJsParameters.language},
        fetchPolicy: 'network-only'
    });

    useEffect(() => {
        if (loading === false && data) {
            setTextArea(data?.jcr?.nodeByPath?.property?.value);
        }
    }, [loading, data]);

    const [updateLlms] = useMutation(LlmsMutation);

    if (loading) {
        return 'Loading ...';
    }

    if (error) {
        console.error(error);
        return 'There was an error reading llms configuration';
    }

    const initialValue = data?.jcr?.nodeByPath?.property?.value;
    const handleClick = () => updateLlms({
        variables: {
            path: sitePath,
            value: textArea,
            publish: data?.jcr?.nodeByPath?.aggregatedPublicationInfo?.publicationStatus === 'PUBLISHED'
        }, refetchQueries: refetch
    }).then(() => notificationContext.notify(t('notification.save'), ['closeButton'], {autoHideDuration: 3000}));
    return (

        <>
            <Header title={t('header', {siteName: data?.jcr?.nodeByPath?.displayName})}
                    mainActions={[
                        <Button key="llmsSaveButton"
                                size="big"
                                id="llmsSaveButton"
                                color="accent"
                                isDisabled={textArea === initialValue}
                                label={t('save')}
                                onClick={handleClick}/>
                    ]}
                    toolbarRight={[
                        <Button key="bookIcon" label="Academy" icon={<Book/>} variant="ghost" onClick={() => window.open(`https://academy.jahia.com/documentation/enduser/jahia/${window.contextJsParameters.version}/advanced-authoring/seo/llms`, '_blank')}/>
                    ]}/>
            <div className={styles.llmsContainer}>
                <div className={styles.textContainer}>
                    <Typography className={styles.header} variant="heading">{t('heading')}</Typography>
                    <Typography className={styles.description} variant="caption" color="grey">{t('description')}</Typography>
                </div>
                <textarea id="llmsTextArea" className={styles.llmsTextArea} value={textArea} onChange={e => setTextArea(e.target.value)}/>
            </div>
        </>
    );
};
