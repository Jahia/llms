import React, {useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Loader, Typography} from '@jahia/moonstone';
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
 * While it runs it is the block that says so, in the colour of an information; once over it stays under
 * the result, so the editor can read what was left out and why. The same element in both cases: only
 * its name and its colour change, so focus and scroll position in the log survive the end.
 *
 * The bar and the log are not live regions: the panel reads the server about once a second, and a
 * screen reader told every line would be told two hundred. One hidden status says the start, each
 * quarter and each new failure; the banner of the card says the end.
 */
export const GenerationProgress = ({progress, isRunning, fileName}) => {
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

    // A generation that has not listed its pages yet is a new one: what the last one logged goes.
    useEffect(() => {
        if (!isStarted) {
            logged.current = new Set();
            lastStep.current = -1;
            lastFailed.current = 0;
            setLines(previous => (previous.length > 0 ? [] : previous));
            setSpoken('');
        }
    }, [isStarted]);

    useEffect(() => {
        const fresh = pages.filter(page => page.state !== PENDING && !logged.current.has(page.url));
        if (fresh.length > 0) {
            fresh.forEach(page => logged.current.add(page.url));
            setLines(previous => [...previous, ...fresh]);
        }
    }, [pages]);

    useEffect(() => {
        // Only a generation that runs is told: the same list shown after it is over has nothing new to say.
        if (!isStarted || !isRunning) {
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
    }, [isStarted, isRunning, total, done, pages, t]);

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
        <div className={isRunning ? `${styles.progress} ${styles.progressRunning}` : styles.progress}
             role="group"
             aria-labelledby="llms-progress-label"
             data-llms-role="progress"
             data-llms-running={isRunning ? 'true' : 'false'}
        >
            <Typography id="llms-progress-label" weight="bold">
                {isRunning ? t('generating.titleFile', {file: fileName, interpolation: {escapeValue: false}}) : t('progress.done')}
            </Typography>
            <div className={styles.progressRow}>
                {/* The bar says how far; the spinner says it is still going, when a page is slow to render. */}
                {isRunning && <Loader size="small" aria-hidden="true"/>}
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
    isRunning: PropTypes.bool,
    fileName: PropTypes.string.isRequired,
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
