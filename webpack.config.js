const path = require('path');
const getModuleFederationConfig = require('@jahia/webpack-config/getModuleFederationConfig');
const {CleanWebpackPlugin} = require('clean-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const ModuleFederationPlugin = require('webpack/lib/container/ModuleFederationPlugin');
const {CycloneDxWebpackPlugin} = require('@cyclonedx/webpack-plugin');
const packageJson = require('./package.json');

module.exports = (env, argv) => {
    const isProduction = argv.mode === 'production';
    return {
        entry: {main: path.resolve(__dirname, 'src/javascript/index')},
        output: {
            path: path.resolve(__dirname, 'src/main/resources/javascript/apps/'),
            publicPath: 'auto',
            filename: 'llms.bundle.js',
            chunkFilename: '[name].llms.[chunkhash:6].js'
        },
        resolve: {extensions: ['.mjs', '.js', '.jsx']},
        module: {
            rules: [
                {test: /\.jsx?$/, use: 'babel-loader', exclude: /node_modules/},
                {
                    test: /\.module\.css$/,
                    use: ['style-loader', {loader: 'css-loader', options: {modules: {namedExport: false, exportLocalsConvention: 'asIs'}}}]
                },
                {test: /\.css$/, exclude: /\.module\.css$/, use: ['style-loader', 'css-loader']}
            ]
        },
        plugins: [
            new ModuleFederationPlugin(getModuleFederationConfig(packageJson, {
                name: 'llms',
                library: {type: 'assign', name: 'appShell.remotes.llms'},
                filename: 'remoteEntry.js',
                exposes: {
                    './init': './src/javascript/init'
                },
                remotes: {
                    '@jahia/app-shell': 'appShellRemote'
                },
                shared: {
                    react: {singleton: true, requiredVersion: packageJson.dependencies.react},
                    'react-dom': {singleton: true, requiredVersion: packageJson.dependencies['react-dom']},
                    // Host-provided, never bundled: the jContent store and Apollo client are the ones we use.
                    'react-redux': {singleton: true, import: false},
                    redux: {singleton: true, import: false},
                    '@apollo/client': {singleton: true, import: false}
                }
            })),
            new CleanWebpackPlugin({verbose: false}),
            // app-shell discovers the remote through the "jahia.remotes" key of this file.
            new CopyWebpackPlugin({patterns: [{from: './package.json', to: ''}]}),
            new CycloneDxWebpackPlugin({specVersion: '1.4', rootComponentType: 'library', outputLocation: './bom'})
        ],
        mode: isProduction ? 'production' : 'development',
        devtool: isProduction ? 'source-map' : 'eval-source-map'
    };
};
