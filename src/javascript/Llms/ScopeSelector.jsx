import React from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Dropdown, Paper, Typography} from '@jahia/moonstone';
import {MODULE} from './module';
import styles from './Llms.module.css';

// The Dropdown takes no empty value: the whole site has a name of its own in the list.
const WHOLE_SITE = '__site__';

/**
 * What the two files are about: the whole site, or one section of it (a page and the pages below it,
 * whose files are served under the path of that page). It sits before the panel of the tabs because it
 * applies to both files; choosing reloads them.
 *
 * A choice made with unsaved changes is refused and said so next to the control: nothing changes, and
 * nothing is lost.
 */
export const ScopeSelector = ({siteName, sections, scope, refusal, onSelect}) => {
    const {t} = useTranslation(MODULE);
    const data = [
        {label: t('scope.site', {name: siteName}), value: WHOLE_SITE},
        ...sections.map(section => ({
            label: section.title,
            value: section.path,
            // Two pages can bear the same title: the path tells them apart.
            description: section.path
        }))
    ];

    return (
        <Paper hasPadding className={styles.block} data-llms-role="scope">
            <div className={styles.field}>
                <Typography id="llms-scope-label" weight="bold">{t('scope.label')}</Typography>
                <Typography id="llms-scope-help" variant="caption" className={styles.muted}>{t('scope.help')}</Typography>
                {/* The Dropdown names itself after its value: the group around it carries the field's name. */}
                <Dropdown data={data}
                          value={scope || WHOLE_SITE}
                          variant="outlined"
                          role="group"
                          aria-label={t('scope.label')}
                          aria-describedby="llms-scope-help llms-scope-refusal"
                          searchEmptyText={t('scope.noResult')}
                          onChange={(e, item) => {
                              // Returns nothing: a returned false would keep the menu open.
                              onSelect(item.value === WHOLE_SITE ? null : item.value);
                          }}/>
                {/* Always in the page, so that the refusal is announced when its text arrives. */}
                <Typography id="llms-scope-refusal" variant="caption" className={styles.error} role="alert" data-llms-role="scope-refusal">
                    {refusal}
                </Typography>
            </div>
        </Paper>
    );
};

ScopeSelector.propTypes = {
    siteName: PropTypes.string.isRequired,
    sections: PropTypes.arrayOf(PropTypes.shape({
        path: PropTypes.string.isRequired,
        title: PropTypes.string.isRequired
    })).isRequired,
    scope: PropTypes.string,
    refusal: PropTypes.string.isRequired,
    onSelect: PropTypes.func.isRequired
};
