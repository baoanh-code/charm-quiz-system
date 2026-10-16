import { useRef, useState } from 'react';
import { NavLink, Navigate, Outlet, useNavigate } from 'react-router-dom';
import { useManagerAuth } from './ManagerAuthContext';
import ManagerAuthStatus from './ManagerAuthStatus';

function RequireManagerAuth() {
  const { status, user, signOut } = useManagerAuth();
  const navigate = useNavigate();
  const signingOutRef = useRef(false);
  const [isSigningOut, setIsSigningOut] = useState(false);
  const [logoutError, setLogoutError] = useState('');

  if (status === 'unauthenticated' || status === 'unconfigured') {
    return <Navigate to="/manager/login" replace />;
  }
  if (status !== 'authenticated') return <ManagerAuthStatus />;

  async function handleLogout() {
    if (signingOutRef.current) return;
    signingOutRef.current = true;
    setIsSigningOut(true);
    setLogoutError('');

    try {
      await signOut();
      navigate('/manager/login', { replace: true });
    } catch {
      setLogoutError('Could not sign out. Please try again.');
    } finally {
      signingOutRef.current = false;
      setIsSigningOut(false);
    }
  }

  return (
    <div className="manager-shell">
      <header className="manager-header">
        <div className="manager-header__inner">
          <span className="manager-header__brand">Your Story Your Charm <span>Manager</span></span>
          <nav className="manager-header__nav" aria-label="Manager navigation">
            <NavLink to="/manager" end>Charms</NavLink>
            <NavLink to="/manager/rules">Rules</NavLink>
            <NavLink to="/manager/quizzes">Quiz data</NavLink>
          </nav>
          <button className="button button--secondary" type="button" onClick={handleLogout} disabled={isSigningOut}>
            {isSigningOut ? 'Signing out...' : 'Sign out'}
          </button>
        </div>
      </header>
      <main className="manager-main">
        {logoutError ? <p className="manager-form-error" role="alert">{logoutError}</p> : null}
        <Outlet context={{ user }} />
      </main>
    </div>
  );
}

export default RequireManagerAuth;
