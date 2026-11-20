import React from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Typography} from '@jahia/moonstone';
import {MODULE} from './module';
import styles from './Llms.module.css';

// The "No index" option the sitemap module adds to the SEO options of a page.
const NO_INDEX = 'jseomix:noIndex';
// Type names are written as they are: nothing here is inserted as HTML.
const PLAIN = {interpolation: {escapeValue: false}};

/**
 * Which pages are left out of both files, said where the editor generates them. The list is the one in
 * force on this Jahia: the types an administrator configured are named, so a missing page has a reason
 * the editor can read here and act on (unmark the page, or ask for the configuration).
 */
export const GenerationRules = ({rules}) => {
    const {t} = useTranslation(MODULE);
    const excluded = rules?.excludedTypes || [];
    const otherTypes = excluded.filter(type => type !== NO_INDEX);
    const trees = rules?.excludedTreeTypes || [];
    const marks = rules?.includedTypes || [];

    return (
        <div className={`${styles.rules} ${styles.prose}`} data-llms-role="rules">
            <Typography id="llms-rules-title" weight="bold">{t('rules.title')}</Typography>
            <ul className={styles.rulesList} aria-labelledby="llms-rules-title">
                <Typography component="li">{t('rules.hidden')}</Typography>
                <Typography component="li">{t('rules.untranslated')}</Typography>
                {excluded.includes(NO_INDEX) && (
                    <Typography component="li" data-llms-rule="noIndex">{t('rules.noIndex')}</Typography>
                )}
                {otherTypes.length > 0 && (
                    <Typography component="li" data-llms-rule="types">
                        {t('rules.types', {types: otherTypes.join(', '), ...PLAIN})}
                    </Typography>
                )}
                {marks.length > 0 && (
                    <Typography component="li" data-llms-rule="marks">
                        {t('rules.marks', {types: marks.join(', '), ...PLAIN})}
                    </Typography>
                )}
                {trees.length > 0 && (
                    <Typography component="li" data-llms-rule="trees">
                        {t('rules.trees', {types: trees.join(', '), ...PLAIN})}
                    </Typography>
                )}
            </ul>
            {rules && (
                <Typography className={styles.muted} data-llms-rule="limits">
                    {t(rules.maxMainResources > 0 ? 'rules.limitsWithContents' : 'rules.limits',
                        {pages: rules.maxPages, depth: rules.maxDepth, contents: rules.maxMainResources})}
                </Typography>
            )}
        </div>
    );
};

GenerationRules.propTypes = {
    rules: PropTypes.shape({
        excludedTypes: PropTypes.arrayOf(PropTypes.string),
        excludedTreeTypes: PropTypes.arrayOf(PropTypes.string),
        includedTypes: PropTypes.arrayOf(PropTypes.string),
        maxMainResources: PropTypes.number,
        maxPages: PropTypes.number,
        maxDepth: PropTypes.number
    })
};
