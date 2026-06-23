/* eslint-disable react/prop-types */
import React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';
import {useQuery, useMutation} from '@apollo/react-hooks';
import {useNotifications} from '@jahia/react-material';
import LlmsComponent from './Llms.component';

// Factory mocks keep the real (heavy) modules from loading at all.
jest.mock('@apollo/react-hooks', () => ({
    useQuery: jest.fn(),
    useMutation: jest.fn()
}));

jest.mock('@jahia/react-material', () => ({
    useNotifications: jest.fn()
}));

// React-i18next: t(key) -> key so assertions can target i18n keys directly.
jest.mock('react-i18next', () => ({
    useTranslation: () => ({t: key => key})
}));

// Moonstone primitives reduced to plain DOM so we can assert on the Save button.
jest.mock('@jahia/moonstone', () => ({
    Book: () => null,
    Header: ({title, mainActions}) => (
        <div>
            <span>{title}</span>
            {mainActions}
        </div>
    ),
    Button: ({label, isDisabled, onClick, id}) => (
        <button type="button" id={id} disabled={isDisabled} onClick={onClick}>{label}</button>
    ),
    Typography: ({children}) => <span>{children}</span>
}));

const SITE_KEY = 'mySite';
const SITE_PATH = '/sites/mySite';

const buildData = value => ({
    jcr: {
        nodeByPath: {
            displayName: 'My Site',
            property: value === undefined ? null : {value},
            aggregatedPublicationInfo: {publicationStatus: 'PUBLISHED'}
        }
    }
});

describe('Llms.component', () => {
    let notifyMock;
    let mutationMock;
    let refetchMock;

    beforeEach(() => {
        window.contextJsParameters = {siteKey: SITE_KEY, language: 'en', version: '8.2'};
        notifyMock = jest.fn();
        refetchMock = jest.fn();
        mutationMock = jest.fn(() => Promise.resolve());

        useNotifications.mockReturnValue({notify: notifyMock});
        useMutation.mockReturnValue([mutationMock]);
    });

    it('shows a loading indicator while the query is loading', () => {
        useQuery.mockReturnValue({data: undefined, error: undefined, loading: true, refetch: refetchMock});

        render(<LlmsComponent/>);

        expect(screen.getByText('loading')).toBeInTheDocument();
    });

    it('shows an error message when the query fails', () => {
        const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
        useQuery.mockReturnValue({data: undefined, error: new Error('boom'), loading: false, refetch: refetchMock});

        render(<LlmsComponent/>);

        expect(screen.getByText('error')).toBeInTheDocument();
        errorSpy.mockRestore();
    });

    it('populates the textarea with the j:llms value and exposes an accessible name', () => {
        useQuery.mockReturnValue({data: buildData('hello llms'), error: undefined, loading: false, refetch: refetchMock});

        render(<LlmsComponent/>);

        // Accessible name comes from aria-label={t('heading')}
        const textarea = screen.getByRole('textbox', {name: 'heading'});
        expect(textarea).toHaveValue('hello llms');
    });

    it('defaults the textarea to an empty string when the property is absent', () => {
        useQuery.mockReturnValue({data: buildData(undefined), error: undefined, loading: false, refetch: refetchMock});

        render(<LlmsComponent/>);

        const textarea = screen.getByRole('textbox', {name: 'heading'});
        expect(textarea).toHaveValue('');
    });

    it('enables Save when the textarea changes and saves with the expected variables', async () => {
        useQuery.mockReturnValue({data: buildData('initial'), error: undefined, loading: false, refetch: refetchMock});

        render(<LlmsComponent/>);

        const saveButton = screen.getByText('save');
        // Disabled while value equals the initial value.
        expect(saveButton).toBeDisabled();

        const textarea = screen.getByRole('textbox', {name: 'heading'});
        fireEvent.change(textarea, {target: {value: 'changed content'}});

        expect(saveButton).not.toBeDisabled();

        fireEvent.click(saveButton);

        expect(mutationMock).toHaveBeenCalledWith({
            variables: {
                path: SITE_PATH,
                value: 'changed content',
                publish: true
            }
        });

        // The mutation promise resolves, then refetch + notify run.
        await Promise.resolve();
        expect(refetchMock).toHaveBeenCalled();
        expect(notifyMock).toHaveBeenCalledWith('notification.save', ['closeButton'], {autoHideDuration: 3000});
    });
});
