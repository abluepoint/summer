import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import svgrPlugin from '@arco-plugins/vite-plugin-svgr';
import vitePluginForArco from '@arco-plugins/vite-react';
import setting from './src/settings.json';

var backendOrigin =
  process.env.SUMMER_BACKEND_ORIGIN || 'http://127.0.0.1:8080';

// https://vitejs.dev/config/
export default defineConfig({
  server: {
    host: '127.0.0.1',
    proxy: {
      '/api': backendOrigin,
      '/oauth2': {
        target: backendOrigin,
        bypass: (request) => {
          if (
            request.method === 'GET' &&
            request.url?.startsWith('/oauth2/callback')
          ) {
            return '/index.html';
          }
        },
      },
      '/.well-known': backendOrigin,
      '/connect': backendOrigin,
      '/csrf': backendOrigin,
      '/login': {
        target: backendOrigin,
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
