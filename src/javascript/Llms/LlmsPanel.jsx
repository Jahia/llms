import React from 'react';
import {useSelector} from 'react-redux';
import {useTranslation} from 'react-i18next';
import {useNodeChecks} from '@jahia/data-helper';
import {Header, LayoutContent, Loader, Typography} from '@jahia/moonstone';
import {LlmsEditor} from './LlmsEditor';
import {MODULE} from './module';
import styles from './Llms.module.css';

/**
 * The route. requireModuleInstalledOnSite on the route only hides the menu entry: a direct URL or a
 * site switch renders the panel on any site, so the module and the permission are checked here again.
 */
export const LlmsPanel = () => {
    const {t} = useTranslation(MODULE);
    const siteKey = useSelector(state => state.site);
    const {checksResult, loading} = useNodeChecks(
        {path: `/sites/${siteKey}`},
        {requireModuleInstalledOnSite: [MODULE], requiredPermission: ['publish']}
    );

    if (loading) {
        return <div className={styles.root}><Loader size="big"/></div>;
    }

    if (!checksResult) {
        return (
            <div className={styles.root}>
                <LayoutContent
                    hasPadding
                    header={<Header title={t('navLabel')}/>}
                    content={(
                        <div className={styles.content}>
                            <div className={styles.card} role="status">
                                <Typography variant="heading" component="h2">{t('disabled.title')}</Typography>
                                <Typography>{t('disabled.text')}</Typography>
                            </div>
                        </div>
                    )}
                />
            </div>
        );
    }

    // Remounted per site, so no draft of one site survives a switch to another.
    return <LlmsEditor key={siteKey} siteKey={siteKey}/>;
};
