import React from 'react';
import { useLocation } from 'react-router-dom';
import CustomerScreen from '../CustomerScreen';
import { detailSource } from '../contract.mjs';
import { readCustomerDraft } from '@/auth/oidc';

export default function CustomerDetailPage() {
  const location = useLocation<{ from?: string }>();
  const source = detailSource(location.state?.from);
  const username = location.pathname.slice('/customer/detail/'.length);
  const recovered = readCustomerDraft(sessionStorage);
  const recoveredMode =
    recovered?.editUsername === username &&
    (recovered.mode === 'drawer' || recovered.mode === 'modal')
      ? recovered.mode
      : undefined;
  const mode =
    (source?.mode || recoveredMode) === 'modal'
      ? 'modal'
      : source?.mode === 'page'
      ? 'page'
      : 'drawer';
  return <CustomerScreen mode={mode} sourceUrl={source?.url} />;
}
