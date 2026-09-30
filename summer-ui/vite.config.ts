import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import svgrPlugin from '@arco-plugins/vite-plugin-svgr';
import vitePluginForArco from '@arco-plugins/vite-react';
import setting from './src/settings.json';

// https://vitejs.dev/config/
export default defineConfig({
  server: {
    host: '127.0.0.1',
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/oauth2': {
        target: 'http://127.0.0.1:8080',
        bypass: (request) => {
          if (
            request.method === 'GET' &&
            request.url?.startsWith('/oauth2/callback')
          ) {
            return '/index.html';
          }
        },
      },
      '/.well-known': 'http://127.0.0.1:8080',
      '/connect': 'http://127.0.0.1:8080',
      '/csrf': 'http://127.0.0.1:8080',
      '/login': {
        target: 'http://127.0.0.1:8080',
        bypass: (request) => {
          if (request.method === 'GET') return '/index.html';
        },
      },
    },
  },
  resolve: {
    alias: [{ find: '@', replacement: '/src' }],
  },
  plugins: [
    react(),
    svgrPlugin({
      svgrOptions: {},
    }),
    vitePluginForArco({
      theme: '@arco-themes/react-arco-pro',
      modifyVars: {
        'arcoblue-6': setting.themeColor,
      },
    }),
  ],
  css: {
    preprocessorOptions: {
      less: {
        javascriptEnabled: true,
      },
    },
  },
});
