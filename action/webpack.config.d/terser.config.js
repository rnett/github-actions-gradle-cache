const TerserPlugin = require('terser-webpack-plugin');

config.optimization = {
  minimizer: [
    new TerserPlugin({
      parallel: true,
      terserOptions: {
        // https://github.com/webpack-contrib/terser-webpack-plugin#terseroptions
        mangle: false,
        // compress: false,
        keep_classnames: /AbortSignal/,
        keep_fnames: /AbortSignal/
      }
    }),
  ],
};