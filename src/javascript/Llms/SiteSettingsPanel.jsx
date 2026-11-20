import React, {useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {useMutation} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Banner, Button, Input, Paper, Typography} from '@jahia/moonstone';
import {LLMS_SAVE_SETTINGS} from './Llms.gql';
import {MODULE} from './module';
import styles from './Llms.module.css';

// Type names are written as they are: nothing here is inserted as HTML.
const PLAIN = {interpolation: {escapeValue: false}};
const NUMBER = /^\d{1,6}$/;
// A prefixed name (jseomix:noIndex), as the server reads one.
const TYPE_NAME = /^[A-Za-z][A-Za-z0-9_]*:[A-Za-z][A-Za-z0-9_.-]*$/;
const NONE = 'none';

const valuesOf = fields => Object.fromEntries(fields.map(field => [field.key, field.value]));

const errorsOf = fields => Object.fromEntries(fields.filter(field => field.error)
    .map(field => [field.key, {code: field.error, detail: field.detail}]));

/**
 * What the browser can tell before asking the server: a number that is not one or is out of range, a
 * name that is not shaped like a type. Whether a type exists on this Jahia is for the server to say.
 */
const check = (field, value) => {
    const text = value.trim();
    if (!text) {
        return null;
    }

    if (field.kind === 'NUMBER') {
        if (!NUMBER.test(text)) {
            return {code: 'number'};
        }

        const number = Number(text);
        return number < field.min || number > field.max ? {code: 'range'} : null;
    }

    if (field.kind === 'TYPES' && text !== NONE) {
        const wrong = text.split(/[,\s]+/).filter(name => name && !TYPE_NAME.test(name));
        return wrong.length > 0 ? {code: 'types', detail: wrong.join(', ')} : null;
    }

    return null;
};

/** The first control of a field: its input, or the first choice of its group. */
const controlId = field => (field.kind === 'FLAG' ? `llms-set-${field.key}-default` : `llms-set-${field.key}`);

const inheritedText = (t, field) => {
    if (field.kind === 'FLAG') {
        return t(field.inherited === 'true' ? 'config.choice.yes' : 'config.choice.no');
    }

    return field.inherited || t('config.nothing');
};

/** A number or a list of types: a labelled input, what it means, what an empty one inherits, its error. */
const TextSetting = ({field, value, error, onChange}) => {
    const {t} = useTranslation(MODULE);
    const id = `llms-set-${field.key}`;
    return (
        <div className={styles.field} data-llms-setting={field.key}>
            <Typography component="label" htmlFor={id} weight="bold">{t(`config.${field.key}.label`)}</Typography>
            <Typography id={`${id}-help`} variant="caption" className={styles.muted}>
                {t(`config.${field.key}.help`, {min: field.min, max: field.max})}
                {' '}
                {t('config.inherit', {value: inheritedText(t, field), ...PLAIN})}
            </Typography>
            <Input id={id}
                   size="big"
                   variant="outlined"
                   value={value}
                   // What applies while the field is empty, shown in it; the help under the label says it too.
                   placeholder={inheritedText(t, field)}
                   autoComplete="off"
                   spellCheck={false}
                   inputMode={field.kind === 'NUMBER' ? 'numeric' : 'text'}
                   aria-invalid={Boolean(error)}
                   aria-describedby={error ? `${id}-help ${id}-error` : `${id}-help`}
                   onChange={e => onChange(e.target.value)}/>
            {error && (
                <Typography id={`${id}-error`} variant="caption" className={styles.error} data-llms-role="setting-error">
                    {t(`config.errors.${error.code}`, {min: field.min, max: field.max, detail: error.detail, ...PLAIN})}
                </Typography>
            )}
        </div>
    );
};

const fieldShape = PropTypes.shape({
    key: PropTypes.string.isRequired,
    kind: PropTypes.string.isRequired,
    value: PropTypes.string.isRequired,
    inherited: PropTypes.string.isRequired,
    min: PropTypes.number.isRequired,
    max: PropTypes.number.isRequired
});

TextSetting.propTypes = {
    field: fieldShape.isRequired,
    value: PropTypes.string.isRequired,
    error: PropTypes.shape({code: PropTypes.string.isRequired, detail: PropTypes.string}),
    onChange: PropTypes.func.isRequired
};

/** Yes, no, or as this Jahia says: three choices in a group named by the setting. */
const ChoiceSetting = ({field, value, onChange}) => {
    const {t} = useTranslation(MODULE);
    const name = `llms-set-${field.key}`;
    const choices = [
        {id: 'default', value: '', label: t('config.choice.default', {value: inheritedText(t, field)})},
        {id: 'yes', value: 'true', label: t('config.choice.yes')},
        {id: 'no', value: 'false', label: t('config.choice.no')}
    ];
    return (
        <fieldset className={styles.choice} aria-describedby={`${name}-help`} data-llms-setting={field.key}>
            <Typography component="legend" weight="bold">{t(`config.${field.key}.label`)}</Typography>
            <Typography id={`${name}-help`} variant="caption" className={styles.muted}>
                {t(`config.${field.key}.help`)}
            </Typography>
            {choices.map(choice => (
                <label key={choice.id} className={styles.choiceRow} htmlFor={`${name}-${choice.id}`}>
                    <input type="radio"
                           id={`${name}-${choice.id}`}
                           name={name}
                           value={choice.value}
                           checked={value === choice.value}
                           onChange={() => onChange(choice.value)}/>
                    <Typography component="span">{choice.label}</Typography>
                </label>
            ))}
        </fieldset>
    );
};

ChoiceSetting.propTypes = {
    field: fieldShape.isRequired,
    value: PropTypes.string.isRequired,
    onChange: PropTypes.func.isRequired
};

/**
 * What this site lists differently from the others: each value with what it means, the range it accepts
 * and what an empty one inherits. Saving writes the configuration of the site; nothing is written while
 * a value is refused. The limits that protect the server are not here: they are the same for every site.
 */
export const SiteSettingsPanel = ({siteKey, fields, onSaved, onAnnounce}) => {
    const {t} = useTranslation(MODULE);
    const [current, setCurrent] = useState(fields);
    const [values, setValues] = useState(() => valuesOf(fields));
    const [errors, setErrors] = useState({});
    const [failure, setFailure] = useState(null);
    // Counts the saves that were refused: each one moves the focus to the first value to correct.
    const [refused, setRefused] = useState(0);
    const [saveSettings, {loading: saving}] = useMutation(LLMS_SAVE_SETTINGS);

    useEffect(() => {
        if (refused > 0) {
            const first = current.find(field => errors[field.key]);
            const control = first && document.getElementById(controlId(first));
            if (control) {
                control.focus();
            }
        }
        // After a refused save only: correcting a value must not move the focus.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [refused]);

    const change = (key, value) => {
        setValues(previous => ({...previous, [key]: value}));
        // What was wrong is being corrected: its error goes, and comes back at the next save if need be.
        setErrors(previous => Object.fromEntries(Object.entries(previous).filter(([name]) => name !== key)));
    };

    const refuse = found => {
        setErrors(found);
        setRefused(n => n + 1);
    };

    const save = async () => {
        if (saving) {
            return;
        }

        setFailure(null);
        onAnnounce('');
        const found = Object.fromEntries(current.map(field => [field.key, check(field, values[field.key])]).filter(([, error]) => error));
        if (Object.keys(found).length > 0) {
            refuse(found);
            return;
        }

        try {
            const {data} = await saveSettings({
                variables: {siteKey, keys: current.map(field => field.key), values: current.map(field => values[field.key].trim())}
            });
            const stored = data.llms.saveSettings;
            const refusedByServer = errorsOf(stored);
            if (Object.keys(refusedByServer).length > 0) {
                refuse(refusedByServer);
                return;
            }

            setCurrent(stored);
            setValues(valuesOf(stored));
            setErrors({});
            const toasts = globalThis.jahia?.toastDispatcher;
            if (toasts) {
                toasts.add({message: t('config.saved'), variant: 'success'});
            }

            onAnnounce(t('config.saved'));
            onSaved();
        } catch (e) {
            setFailure(e?.graphQLErrors?.[0]?.message || e?.message || '');
        }
    };

    const reset = () => {
        setValues(Object.fromEntries(current.map(field => [field.key, ''])));
        setErrors({});
        onAnnounce(t('config.resetDone'));
    };

    const errorCount = Object.keys(errors).length;
    const isDirty = current.some(field => values[field.key].trim() !== field.value);

    return (
        <Paper hasPadding className={styles.block}>
            <Typography id="llms-settings-title" variant="heading" component="h2">{t('config.title')}</Typography>
            <Typography className={`${styles.muted} ${styles.prose}`}>{t('config.description')}</Typography>
            {failure !== null && (
                <Banner variant="danger" title={t('config.errors.save')} role="alert">{failure}</Banner>
            )}
            {errorCount > 0 && (
                <Banner variant="danger" title={t('config.errors.summary', {count: errorCount})} role="alert" data-llms-role="settings-errors"/>
            )}
            <form noValidate
                  className={styles.settingsForm}
                  aria-labelledby="llms-settings-title"
                  data-llms-role="settings"
                  onSubmit={e => {
                      e.preventDefault();
                      save();
                  }}
            >
                {current.map(field => (field.kind === 'FLAG' ? (
                    <ChoiceSetting key={field.key} field={field} value={values[field.key]} onChange={value => change(field.key, value)}/>
                ) : (
                    <TextSetting key={field.key}
                                 field={field}
                                 value={values[field.key]}
                                 error={errors[field.key]}
                                 onChange={value => change(field.key, value)}/>
                )))}
                <div className={styles.actions}>
                    {/* Never disabled: a disabled button drops the focus, and says nothing of why. */}
                    <Button type="submit"
                            color="accent"
                            data-llms-role="save-settings"
                            label={t('config.save')}
                            aria-disabled={saving ? 'true' : undefined}
                            onClick={e => {
                                e.preventDefault();
                                save();
                            }}/>
                    <Button type="button" data-llms-role="reset-settings" label={t('config.reset')} onClick={reset}/>
                    {isDirty && (
                        <Typography variant="caption" className={styles.muted} data-llms-role="settings-unsaved">
                            {t('config.unsaved')}
                        </Typography>
                    )}
                </div>
            </form>
            <Typography className={`${styles.muted} ${styles.prose}`} data-llms-role="settings-server">{t('config.server')}</Typography>
        </Paper>
    );
};

SiteSettingsPanel.propTypes = {
    siteKey: PropTypes.string.isRequired,
    fields: PropTypes.arrayOf(fieldShape).isRequired,
    onSaved: PropTypes.func.isRequired,
    onAnnounce: PropTypes.func.isRequired
};
