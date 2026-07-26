'use client';

import { Cloud, CloudOff } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { useOfflineStore } from '@/store/offline-store';
import styles from './offline-indicator.module.css';

export function OfflineIndicator() {
  const t = useTranslations('common');
  const [online, setOnline] = useState(true);
  const pending = useOfflineStore((state) => state.pendingMutations.length);
  const syncing = useOfflineStore((state) => state.isSyncing);

  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    update();
    window.addEventListener('online', update);
    window.addEventListener('offline', update);
    return () => {
      window.removeEventListener('online', update);
      window.removeEventListener('offline', update);
    };
  }, []);

  if (online && pending === 0 && !syncing) return null;
  const isOffline = !online;
  const label = isOffline
    ? t('offline')
    : syncing
      ? t('syncingChanges')
      : t('pendingChanges', { count: pending });

  return (
    <div className={styles.indicator} data-offline={isOffline} data-syncing={syncing} role="status" aria-live="polite">
      {isOffline ? <CloudOff className={styles.icon} aria-hidden="true" /> : <Cloud className={`${styles.icon} ${syncing ? styles.spin : ''}`} aria-hidden="true" />}
      <span>{label}</span>
    </div>
  );
}
