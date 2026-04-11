module.exports = {
  devServer: {
    port: Number(process.env.PORT || 8080),
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
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
