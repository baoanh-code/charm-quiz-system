import { createClient } from '@supabase/supabase-js';

let supabaseClient;

export function getSupabaseClient() {
  const url = import.meta.env?.VITE_SUPABASE_URL;
  const publishableKey = import.meta.env?.VITE_SUPABASE_PUBLISHABLE_KEY;

  if (!url || !publishableKey) return null;

  try {
    supabaseClient ??= createClient(url, publishableKey, {
      auth: {
        autoRefreshToken: true,
        persistSession: true,
      },
    });
  } catch {
    return null;
  }

  return supabaseClient;
}
