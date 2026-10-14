import { Link } from 'react-router-dom';
import { useManagerAuth } from './ManagerAuthContext';

function ManagerAuthStatus() {
  const { status, retrySession } = useManagerAuth();

  return (
    <main className="manager-auth-page">
      <section className="manager-auth-panel" aria-live="polite">
        <p className="manager-eyebrow">Manager access</p>
        <h1>{status === 'loading' ? 'Checking your session' : 'Manager sign-in unavailable'}</h1>
        {status === 'loading' ? <p role="status">Please wait...</p> : null}
        {status === 'unconfigured' ? (
          <p>Sign-in is not configured for this site yet.</p>
        ) : null}
        {status === 'error' ? (
          <>
            <p>We could not check your session. Please try again.</p>
            <button className="button button--primary" type="button" onClick={retrySession}>
              Try again
            </button>
          </>
        ) : null}
        <Link to="/">Return to Home</Link>
      </section>
    </main>
  );
}

export default ManagerAuthStatus;
