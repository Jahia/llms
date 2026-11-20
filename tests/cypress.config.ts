import {defineConfig} from 'cypress';
import {setupNodeEvents} from './cypress/plugins';

export default defineConfig({
    video: false,
    screenshotOnRunFailure: true,
    viewportWidth: 1440,
    viewportHeight: 900,

    defaultCommandTimeout: 10000,
    requestTimeout: 30000,
    // Generating llms-full.txt renders every listed page: allow it its configured deadline.
    responseTimeout: 180000,

    retries: {
        runMode: 1,
        openMode: 0
    },

    // Mochawesome for the report env.run.sh merges, JUnit XML for the Jahia integration-tests action.
    reporter: 'cypress-multi-reporters',
    reporterOptions: {
        configFile: 'reporter-config.json'
    },

    e2e: {
        supportFile: 'cypress/support/e2e.ts',
        specPattern: 'cypress/e2e/**/*.spec.ts',
        setupNodeEvents
    }
});
