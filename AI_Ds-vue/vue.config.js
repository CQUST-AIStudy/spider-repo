module.exports = {
    // 开发服务器配置
    devServer: {
        // 代理配置
        proxy: {
            // 以 /api 开头的请求会被代理
            '/api': {
                // 目标后端服务器地址
                target: 'http://localhost:8081',
                // 允许跨域
                changeOrigin: true,
                // 重写请求路径，去掉 /api 前缀
                pathRewrite: {
                    '^/api': ''
                }
            }
        },

        client: {
            overlay:
                false
        }
    }
};
