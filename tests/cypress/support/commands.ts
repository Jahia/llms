// Registers cy.waitUntil: @jahia/cypress calls it from its publication helpers, and
// registerSupport() does not load the plugin itself.
import 'cypress-wait-until';
import {registerSupport} from '@jahia/cypress/dist/support/registerSupport';

registerSupport();
