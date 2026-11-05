/** Event driven invalidation; callers capture the context of the initiating request. */
export const MENU_REFRESH_EVENT = 'summer:menu-refresh';
export function requestMenuRefresh(contextVersion: number) {
  window.dispatchEvent(
    new CustomEvent(MENU_REFRESH_EVENT, { detail: { contextVersion } })
  );
}
