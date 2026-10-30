import React from 'react';
import i18next from 'i18next';
import {registry} from '@jahia/ui-extender';
import {LlmsPanel} from './Llms/LlmsPanel';
import {MODULE} from './Llms/module';

export default async function register() {
    await i18next.loadNamespaces(MODULE);

    // jContent > Additional > SEO, next to the site's other public files (robots.txt, sitemap).
    registry.add('adminRoute', 'siteSettingsSeo/llms', {
        targets: ['jcontent-siteSettingsSeo:75'],
        label: `${MODULE}:navLabel`,
        isSelectable: true,
        requiredPermission: 'publish',
        requireModuleInstalledOnSite: MODULE,
        render: () => <LlmsPanel/>
    });
}
