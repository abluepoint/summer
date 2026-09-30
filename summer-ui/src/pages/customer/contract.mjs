const MODES = ['drawer', 'modal', 'page'];
const SORTS = ['username', 'email', 'displayName', 'active'];
const USERNAME = /^[A-Za-z0-9][A-Za-z0-9_-]{2,63}$/;

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
  return `/customer/${mode}/${encodeURIComponent(username)}${search}`;
}

export function detailUsername(pathname, mode) {
  if (!MODES.includes(mode)) return null;
  const prefix = `/customer/${mode}/`;
  if (!pathname.startsWith(prefix)) return null;
  try {
    const value = decodeURIComponent(pathname.slice(prefix.length));
    return USERNAME.test(value) ? value : null;
  } catch {
    return null;
  }
}

export function readFailure(body, status, headerRequestId = '') {
  const error = body?.error && typeof body.error === 'object' ? body.error : {};
  return {
    status,
    code: typeof error.code === 'string' ? error.code : '',
    message: typeof error.message === 'string' ? error.message : '',
    violations: Array.isArray(error.violations) ? error.violations : [],
    requestId:
      typeof body?.requestId === 'string' ? body.requestId : headerRequestId,
    timestamp: typeof body?.timestamp === 'string' ? body.timestamp : '',
  };
}

export function readEnvelope(body, status) {
  if (body?.success === true) return body.data;
  throw readFailure(body, status);
}
