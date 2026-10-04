import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Message, Table, Tag } from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import PermissionWrapper from '@/components/PermissionWrapper';
import { getVisibleMenuManifest, MenuManifest, updateMenuManifestOverride } from '@/menu/api';

const enterpriseId = 'ent-acme';

export default function MenuManifestPage() {
  const t = useLocale();
  const [manifest, setManifest] = useState<MenuManifest | null>(null);
  const [error, setError] = useState<unknown>(null);
  const load = () => getVisibleMenuManifest(enterpriseId).then(setManifest).catch(setError);
  useEffect(() => { load(); }, []);
  const disable = (item: MenuManifest['items'][number]) => updateMenuManifestOverride(enterpriseId, item.menuId, { enabled: false }, item.version)
    .then(() => { Message.success(t['menuManifest.saved']); load(); }).catch(setError);
  return (
    <Card title={t['menuManifest.title']} extra={<Button onClick={load}>{t['menuManifest.refresh']}</Button>}>
      {error && <Alert type="error" content={t['menuManifest.error']} />}
      <Table
        rowKey="menuId"
        data={manifest?.items || []}
        pagination={false}
        columns={[
          { title: t['menuManifest.menuId'], dataIndex: 'menuId' },
          { title: t['menuManifest.path'], dataIndex: 'path' },
          { title: t['menuManifest.capability'], dataIndex: 'capabilityCode', render: (value) => value || '-' },
          { title: t['menuManifest.actions'], dataIndex: 'actions', render: (actions: string[]) => actions.map((action) => <Tag key={action}>{action}</Tag>) },
          { title: t['menuManifest.operation'], render: (_, item) => (
            <PermissionWrapper requiredPermissions={[{ resource: 'enterprise.menu', actions: ['write'] }]}>
              <Button size="small" onClick={() => disable(item)}>{t['menuManifest.disable']}</Button>
            </PermissionWrapper>
          ) },
        ]}
      />
    </Card>
  );
}
