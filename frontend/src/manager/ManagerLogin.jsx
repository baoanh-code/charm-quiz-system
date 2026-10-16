import { useRef, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useManagerAuth } from './ManagerAuthContext';
import ManagerAuthStatus from './ManagerAuthStatus';

function ManagerLogin() {
  const { status, signIn } = useManagerAuth();
  const navigate = useNavigate();
  const submittingRef = useRef(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  if (status === 'authenticated') return <Navigate to="/manager" replace />;
  if (status !== 'unauthenticated') return <ManagerAuthStatus />;

  async function handleSubmit(event) {
    event.preventDefault();
    if (submittingRef.current) return;

    submittingRef.current = true;
    setIsSubmitting(true);
    setError('');

    try {
      await signIn(email.trim(), password);
      setPassword('');
      navigate('/manager', { replace: true });
    } catch (signInError) {
      setPassword('');
      setError(signInError?.code === 'invalid_credentials'
        ? 'Email or password is incorrect. Please try again.'
        : 'Could not sign in right now. Please try again.');
    } finally {
      submittingRef.current = false;
      setIsSubmitting(false);
    }
  }

  return (
    <main className="manager-auth-page">
      <section className="manager-auth-panel" aria-labelledby="manager-login-title">
        <p className="manager-eyebrow">Your Story Your Charm</p>
        <h1 id="manager-login-title">Manager sign in</h1>
        <form className="manager-login-form" onSubmit={handleSubmit} aria-busy={isSubmitting}>
          <div className="manager-field">
            <label htmlFor="manager-email">Email</label>
            <input
              id="manager-email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => {
                setEmail(event.target.value);
                setError('');
              }}
              disabled={isSubmitting}
              required
            />
          </div>
          <div className="manager-field">
            <label htmlFor="manager-password">Password</label>
            <input
              id="manager-password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => {
                setPassword(event.target.value);
                setError('');
              }}
              disabled={isSubmitting}
              required
            />
          </div>
          {error ? <p className="manager-form-error" role="alert">{error}</p> : null}
          <button className="button button--primary" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Signing in...' : 'Sign in'}
          </button>
        </form>
        <Link className="manager-home-link" to="/">Return to Home</Link>
      </section>
    </main>
  );
}

export default ManagerLogin;
