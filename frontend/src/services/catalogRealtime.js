import { getSupabaseClient } from './supabaseClient.js';

let activeCleanup;

export function subscribeToCatalogUpdates({
  onInvalidate,
  onStatus,
  client = getSupabaseClient(),
}) {
  activeCleanup?.();

  let closed = false;
  const channel = client
    .channel('customer-catalog-stock')
    .on(
      'postgres_changes',
      {
        event: 'UPDATE',
        schema: 'public',
        table: 'charm',
        filter: 'status=eq.ACTIVE',
      },
      () => {
        if (!closed) onInvalidate();
      },
    )
    .subscribe((status, error) => {
      if (!closed) onStatus(status, error);
    });

  const cleanup = () => {
    if (closed) return;
    closed = true;
    if (activeCleanup === cleanup) activeCleanup = undefined;
    void client.removeChannel(channel);
  };

  activeCleanup = cleanup;
  return cleanup;
}
