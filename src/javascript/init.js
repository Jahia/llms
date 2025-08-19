import {registry} from '@jahia/ui-extender';
import Llms from './Llms';

export default function () {
    registry.add('callback', 'llms', {
        targets: ['jahiaApp-init:24'],
        callback: () => {
            registry.add('adminRoute', 'siteSettingsSeo/llms', {
                targets: ['jcontent-siteSettingsSeo:75'],
                label: 'Llms.txt',
                isSelectable: true,
                requiredPermission: 'publish',
                requireModuleInstalledOnSite: 'llms',
                render: Llms
            });

            console.log('%c Llms registered routes', 'color: #3c8cba');
        }
    });
}
