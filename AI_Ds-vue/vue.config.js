module.exports = {
  devServer: {
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        pathRewrite: {
          '^/api': '',
        },
      },
      '/spider': {
        target: 'http://127.0.0.1:8100',
        changeOrigin: true,
        pathRewrite: {
          '^/spider': '',
        },
      },
    },
    client: {
      overlay: false,
    },
  },
}
