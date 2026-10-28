import React from 'react';
import useLocale from '@/utils/useLocale';

// Keep the first document, authorization callback and menu bootstrap visually
// continuous. Credentials are only requested by the actual login form.
export default function AppLoading({
  fullScreen = true,
  tip,
}: {
  fullScreen?: boolean;
  tip?: string;
}) {
  const t = useLocale();
  return (
    <div
      className={fullScreen ? 'summer-startup' : 'summer-loading-section'}
      role="status"
    >
      <div className="summer-startup-content">
        <span className="summer-startup-spinner" aria-hidden="true" />
        <span>{tip || t['app.loading']}</span>
      </div>
    </div>
  );
}
