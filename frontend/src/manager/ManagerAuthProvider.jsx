import { useEffect, useState } from 'react';
import { Outlet } from 'react-router-dom';
import { getSupabaseClient } from '../services/supabaseClient';
import { ManagerAuthContext } from './ManagerAuthContext';

function ManagerAuthProvider() {
  const [supabase] = useState(getSupabaseClient);
  const [auth, setAuth] = useState({
    status: supabase ? 'loading' : 'unconfigured',
    user: null,
  });
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    if (!supabase) return undefined;

    let active = true;
    const { data: { subscription } } = supabase.auth.onAuthStateChange((event, session) => {
      if (!active || event === 'INITIAL_SESSION') return;
      setAuth({
        status: session?.user ? 'authenticated' : 'unauthenticated',
        user: session?.user ?? null,
      });
    });

    supabase.auth.getSession().then(({ data, error }) => {
      if (!active) return;
      setAuth(error ? { status: 'error', user: null } : {
        status: data.session?.user ? 'authenticated' : 'unauthenticated',
        user: data.session?.user ?? null,
      });
    }).catch(() => {
      if (active) setAuth({ status: 'error', user: null });
    });

    return () => {
      active = false;
      subscription.unsubscribe();
    };
  }, [retryKey, supabase]);

  async function signIn(email, password) {
    const { data, error } = await supabase.auth.signInWithPassword({ email, password });
    if (error || !data.session?.user) throw error ?? new Error('No session returned');
    setAuth({ status: 'authenticated', user: data.session.user });
  }

  async function signOut() {
    const { error } = await supabase.auth.signOut({ scope: 'local' });
    if (error) throw error;
    setAuth({ status: 'unauthenticated', user: null });
  }

  function retrySession() {
    setAuth({ status: 'loading', user: null });
    setRetryKey((key) => key + 1);
  }

  return (
    <ManagerAuthContext.Provider value={{ ...auth, signIn, signOut, retrySession }}>
      <Outlet />
    </ManagerAuthContext.Provider>
  );
}

export default ManagerAuthProvider;
