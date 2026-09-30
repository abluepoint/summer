const LIST_PATH = '/customer/page';

export function pageListReturn(value) {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\')
  )
    return LIST_PATH;
  try {
    const url = new URL(value, 'http://summer.invalid');
    if (url.origin !== 'http://summer.invalid' || url.pathname !== LIST_PATH)
      return LIST_PATH;
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return LIST_PATH;
  }
}

export function pageFormSearch(from) {
  const params = new URLSearchParams();
  params.set('from', pageListReturn(from));
  return `?${params.toString()}`;
}

export function pageFormRoute(pathname) {
  if (pathname === '/customer/page/create') return { action: 'create' };
  const prefix = '/customer/page/edit/';
  if (!pathname.startsWith(prefix)) return null;
  try {
    const username = decodeURIComponent(pathname.slice(prefix.length));
    return username && !username.includes('/')
      ? { action: 'edit', username }
      : null;
  } catch {
    return null;
  }
}
