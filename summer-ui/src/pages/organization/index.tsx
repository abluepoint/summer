import React, { useEffect, useState } from 'react';
import { Alert, Card, Tree, Typography } from '@arco-design/web-react';
import { getOrganizationTree, OrganizationTree, OrganizationApiError, selectOrganizationContext } from './api';

const enterpriseId = 'ent-acme';
export default function OrganizationPage() {
  const [tree, setTree] = useState<OrganizationTree | null>(null);
  const [error, setError] = useState<OrganizationApiError | null>(null);
  useEffect(() => { getOrganizationTree(enterpriseId).then(setTree).catch(setError); }, []);
  const nodes = (tree?.items || []).map((item) => ({ key: item.id, title: `${item.name} (${item.memberCount})`, children: item.children.map((child) => ({ key: child.id, title: `${child.name} (${child.memberCount})` })) }));
  return <Card title="Organization directory"><Typography.Text type="secondary">Enterprise: {enterpriseId}</Typography.Text>{error && <Alert type="error" content={error.message} style={{ margin: '16px 0' }} />}<Tree blockNode treeData={nodes} onSelect={(keys) => { const key = String(keys[0] || ''); if (key) selectOrganizationContext(enterpriseId, key).catch(setError); }} /></Card>;
}
