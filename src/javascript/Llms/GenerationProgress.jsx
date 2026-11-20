import React, {useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Typography} from '@jahia/moonstone';
import {MODULE} from './module';
import styles from './Llms.module.css';

const PENDING = 'PENDING';
const FAILED = 'FAILED';
// Under this many pages a generation is over before a quarter is worth announcing.
const MIN_PAGES_FOR_STEPS = 8;
// How close to its end the log must be scrolled to keep following the newest line.
const FOLLOW_MARGIN = 8;

/**
 * A generation of llms-full.txt as it goes: how many pages are done, and each page as it is rendered.
 * It stays once the generation is over, so the editor can read what was left out and why.
 *
 * The bar and the log are not live regions: the panel reads the server about once a second, and a
 * screen reader told every line would be told two hundred. One hidden status says the start, each
 * quarter and each new failure; the banner of the card says the end.
 */
export const GenerationProgress = ({progress}) => {
    const {t} = useTranslation(MODULE);
    const {isStarted, total, done, pages} = progress;
    // The pages in the order they were done: a log is read from top to bottom, as things happened.
    const [lines, setLines] = useState([]);
    const [spoken, setSpoken] = useState('');
    const logged = useRef(new Set());
    const lastStep = useRef(-1);
    const lastFailed = useRef(0);
    const box = useRef(null);
    const isFollowing = useRef(true);

    useEffect(() => {
        const fresh = pages.filter(page => page.state !== PENDING && !logged.current.has(page.url));
        if (fresh.length > 0) {
            fresh.forEach(page => logged.current.add(page.url));
            setLines(previous => [...previous, ...fresh]);
        }
    }, [pages]);

    useEffect(() => {
        if (!isStarted) {
            return;
        }

        const failed = pages.filter(page => page.state === FAILED).length;
        const step = total >= MIN_PAGES_FOR_STEPS && done < total ? Math.floor((done * 4) / total) : 0;
        if (lastStep.current < 0) {
            lastStep.current = 0;
            setSpoken(t('progress.spokenStart', {total}));
        } else if (failed > lastFailed.current) {
            setSpoken(t('progress.spokenFailed', {count: failed}));
        } else if (step > lastStep.current) {
            lastStep.current = step;
            setSpoken(t('progress.spokenStep', {percent: step * 25, done, total}));
        }

        lastFailed.current = failed;
    }, [isStarted, total, done, pages, t]);

    // The newest line stays in view while the reader is at the end of the log, and only then: someone
    // who scrolled back to read a line, or who is in the log with the keyboard, is not pulled away.
    useEffect(() => {
        const el = box.current;
        if (el && isFollowing.current && document.activeElement !== el) {
            el.scrollTop = el.scrollHeight;
        }
    }, [lines]);

    const onScroll = () => {
        const el = box.current;
        isFollowing.current = el.scrollHeight - el.scrollTop - el.clientHeight <= FOLLOW_MARGIN;
    };

    return (
        <div className={styles.progress} data-llms-role="progress">
            <Typography id="llms-progress-label" weight="bold">{t('progress.label')}</Typography>
            <div className={styles.progressRow}>
                {/* Without a value the bar is indeterminate: the pages are not known until the site is read. */}
                <progress className={styles.bar}
                          aria-labelledby="llms-progress-label"
                          aria-describedby="llms-progress-count"
                          {...(isStarted ? {max: Math.max(total, 1), value: done} : {})}/>
                <Typography id="llms-progress-count" variant="caption" data-llms-role="progress-count">
                    {isStarted ? t('progress.count', {done, total}) : t('progress.preparing')}
                </Typography>
            </div>
            <Typography id="llms-log-title" weight="bold">{t('progress.logTitle')}</Typography>
            {/* Scrolls, so it takes the focus: a keyboard user reads it with the arrow keys. */}
            <div ref={box}
                 className={styles.log}
                 role="region"
                 tabIndex={0}
                 aria-labelledby="llms-log-title"
                 data-llms-role="log"
                 onScroll={onScroll}
            >
                <ul className={styles.logList}>
                    {lines.map(page => (
                        <li key={page.url} className={styles.logLine} data-llms-state={page.state}>
                            <span className={styles.logTitle}>{page.title}</span>
                            <span className={page.state === FAILED ? styles.error : styles.muted}>
                                {t(`progress.state.${page.state}`)}
                            </span>
                        </li>
                    ))}
                </ul>
            </div>
            <div role="status" className={styles.srOnly}>{spoken}</div>
        </div>
    );
};

GenerationProgress.propTypes = {
    progress: PropTypes.shape({
        isStarted: PropTypes.bool.isRequired,
        total: PropTypes.number.isRequired,
        done: PropTypes.number.isRequired,
        pages: PropTypes.arrayOf(PropTypes.shape({
            title: PropTypes.string.isRequired,
            url: PropTypes.string.isRequired,
            state: PropTypes.string.isRequired
        })).isRequired
    }).isRequired
};
