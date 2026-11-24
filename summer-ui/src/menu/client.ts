// Published value in the platform's menu-client dictionary.
const configured = import.meta.env.VITE_MENU_CLIENT_CODE;
export const menuClientCode =
  (typeof configured === 'string' && configured.trim()) || 'web';
