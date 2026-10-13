import { createClient } from '@supabase/supabase-js';

let supabaseClient;

function requiredEnvironmentValue(name) {
  const value = import.meta.env?.[name]?.trim();
  if (!value) {
    throw new Error(`Missing required frontend environment variable: ${name}`);
  }
  return value;
}

export function getSupabaseClient() {
  if (!supabaseClient) {
    supabaseClient = createClient(
      requiredEnvironmentValue('VITE_SUPABASE_URL'),
      requiredEnvironmentValue('VITE_SUPABASE_PUBLISHABLE_KEY'),
      {
        auth: {
          autoRefreshToken: false,
          detectSessionInUrl: false,
          persistSession: false,
        },
      },
    );
  }

  return supabaseClient;
}
