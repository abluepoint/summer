const MODES = ['drawer', 'modal', 'page'];
const SORTS = ['username', 'email', 'displayName', 'active'];
const USERNAME = /^[a-z0-9](?:[a-z0-9._-]{1,62}[a-z0-9])$/;

export function readListState(search) {
  const params = new URLSearchParams(search);
  const page = params.get('page');
  const size = params.get('size');
  const active = params.get('active');
  const sort = params.get('sort');
  const direction = params.get('direction');
  return {
    page: page && /^(0|[1-9]\d{0,8})$/.test(page) ? Number(page) : 0,
    size:
      size && /^(0|[1-9]\d*)$/.test(size) && Number(size) <= 100
        ? Number(size) || 20
        : 20,
    active: active === 'true' || active === 'false' ? active : '',
    sort: SORTS.includes(sort) ? sort : 'username',
    direction: direction === 'desc' ? 'desc' : 'asc',
  };
}

export function listSearch(state) {
  const params = new URLSearchParams();
  params.set('page', String(state.page));
  params.set('size', String(state.size));
  if (state.active) params.set('active', state.active);
  params.set('sort', state.sort);
  params.set('direction', state.direction);
  return `?${params.toString()}`;
}

export function detailPath(mode, username, search = '') {
  if (!MODES.includes(mode) || !USERNAME.test(username))
    throw new Error('Invalid Customer route');
  return `/customer/detail/${encodeURIComponent(username)}${search}`;
}

export function detailUsername(pathname, mode) {
  if (!MODES.includes(mode)) return null;
  const prefix = pathname.startsWith('/customer/detail/')
    ? '/customer/detail/'
    : `/customer/${mode}/`;
  if (!pathname.startsWith(prefix)) return null;
  try {
    const value = decodeURIComponent(pathname.slice(prefix.length));
    return USERNAME.test(value) ? value : null;
  } catch {
    return null;
  }
}

export function detailSource(from) {
  if (typeof from !== 'string' || !from.startsWith('/customer/')) return null;
  try {
    const url = new URL(from, 'http://summer.local');
    const match = /^\/customer\/(drawer|modal|page)$/.exec(url.pathname);
    if (!match || url.origin !== 'http://summer.local') return null;
    return { mode: match[1], url: from };
  } catch {
    return null;
  }
}

export function canConfirmDeletion(typed, username) {
  return (
    typeof username === 'string' && username.length > 0 && typed === username
  );
}

export function pageAfterDeletion(page, size, totalElements) {
  return Math.max(0, Math.min(page, Math.ceil(totalElements / size) - 1));
}
