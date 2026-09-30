const USERNAME = /^[A-Za-z0-9][A-Za-z0-9_-]{2,63}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const FIELDS = ['username', 'email', 'displayName'];

export const emptyCustomerDraft = () => ({
  username: '',
  email: '',
  displayName: '',
});

export function customerDraft(value) {
  const draft = emptyCustomerDraft();
  for (const field of FIELDS) {
    if (typeof value?.[field] === 'string') draft[field] = value[field];
  }
  return draft;
}

export function validateCustomerDraft(value) {
  const draft = customerDraft(value);
  const errors = {};
  if (!USERNAME.test(draft.username)) errors.username = 'username';
  if (!draft.email || draft.email.length > 200 || !EMAIL.test(draft.email))
    errors.email = 'email';
  if (!draft.displayName.trim() || draft.displayName.length > 200)
    errors.displayName = 'displayName';
  return errors;
}

export function customerPayload(value) {
  const draft = customerDraft(value);
  return {
    username: draft.username,
    email: draft.email.trim(),
    displayName: draft.displayName.trim(),
  };
}

export function mapCustomerFailure(failure) {
  const fields = {};
  const other = [];
  for (const violation of failure?.violations || []) {
    const field = violation?.field;
    const message = violation?.message;
    if (FIELDS.includes(field) && typeof message === 'string' && message) {
      fields[field] = message;
    } else if (typeof message === 'string' && message) {
      other.push(message);
    }
  }
  return { fields, other };
}
