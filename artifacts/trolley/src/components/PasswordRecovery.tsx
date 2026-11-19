import { useState } from 'react';
import { recoveryRedirectUrl, supabase } from '../lib/supabase';

type Props = {
  mode: 'request' | 'checking' | 'ready' | 'invalid';
  onSignIn: () => void;
};

const confirmation = 'If an account exists for that email address, you’ll receive a password reset link. Check your inbox and spam folder.';

export function PasswordRecovery({ mode, onSignIn }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [sent, setSent] = useState(false);
  const [saved, setSaved] = useState(false);

  async function requestReset(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    setNotice('');
    if (!supabase) { setNotice('Password recovery is unavailable until accounts are connected. Please try again later.'); return; }
    setBusy(true);
    try {
      await supabase.auth.resetPasswordForEmail(email.trim(), { redirectTo: recoveryRedirectUrl() });
      // Even mail-provider errors can differ for existing/nonexistent users.
      // Keep every server response identical; only network failures are distinct.
      setSent(true);
      setNotice(confirmation);
    } catch {
      setNotice('Unable to connect. Check your connection and try again.');
    } finally { setBusy(false); }
  }

  async function backToSignIn() {
    if (busy) return;
    if (mode === 'ready' && supabase) {
      setBusy(true);
      try {
        const { error } = await supabase.auth.signOut({ scope: 'local' });
        if (error) { setNotice('Unable to sign out of recovery. Please try again.'); return; }
      } catch {
        setNotice('Unable to sign out of recovery. Check your connection and try again.');
        return;
      } finally { setBusy(false); }
    }
    onSignIn();
  }

  async function savePassword(e: React.FormEvent) {
    e.preventDefault();
    if (busy || mode !== 'ready' || !supabase) return;
    setNotice('');
    if (password.length < 8) { setNotice('Use at least 8 characters for your new password.'); return; }
    if (password !== confirmPassword) { setNotice('Your passwords do not match. Please try again.'); return; }
    setBusy(true);
    try {
      const { error } = await supabase.auth.updateUser({ password });
      if (error) {
        setNotice(error.status === 401 || error.status === 403
          ? 'This reset session has expired. Request a new password reset link.'
          : 'Unable to save this password. Use a different password that meets your account’s password requirements, or request a new link.');
        return;
      }
      setPassword('');
      setConfirmPassword('');
      // The password is saved even if clearing the local session fails.
      const { error: signOutError } = await supabase.auth.signOut({ scope: 'local' });
      setSaved(true);
      setNotice(signOutError
        ? 'Your password has been updated. Your session could not be signed out; please sign out before testing your new password.'
        : 'Your password has been updated. Sign in with your new password.');
    } catch {
      setNotice('Unable to confirm the change. Check your connection; try signing in with the new password before requesting another link.');
    } finally { setBusy(false); }
  }

  return <>
    <div className="modal-eyebrow">TROLLEY ACCOUNT RECOVERY</div>
    <h2 id="modal-title">{saved ? 'Password updated' : mode === 'request' ? 'Forgot your password?' : mode === 'invalid' ? 'Request a new reset link' : 'Choose a new password'}</h2>
    {mode === 'checking' && <p role="status" data-testid="status-recovery-checking">Checking your password reset link…</p>}
    {mode === 'invalid' && <p role="alert" data-testid="status-recovery-invalid">This link is invalid, expired, or has already been used. Return to sign in and choose Forgot password to request a new link.</p>}
    {mode === 'request' && !sent && <>
      <p className="modal-intro">Enter your email address and we’ll send instructions to reset your password.</p>
      <form className="auth-form" onSubmit={requestReset}>
        <label>Email address<input data-testid="input-reset-email" type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} disabled={busy}/></label>
        <button data-testid="button-request-reset" className="btn btn-dark auth-submit" disabled={busy}>{busy ? 'Sending…' : 'Send reset link'}</button>
      </form>
    </>}
    {mode === 'ready' && !saved && <form className="auth-form" onSubmit={savePassword}>
      <label>New password<input data-testid="input-new-password" type="password" autoComplete="new-password" required minLength={8} value={password} onChange={e => setPassword(e.target.value)} disabled={busy}/></label>
      <label>Confirm new password<input data-testid="input-confirm-password" type="password" autoComplete="new-password" required minLength={8} value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} disabled={busy}/></label>
      <small className="field-hint">Use at least 8 characters and choose a password you haven’t used before.</small>
      <button data-testid="button-save-password" className="btn btn-dark auth-submit" disabled={busy}>{busy ? 'Saving…' : 'Save new password'}</button>
    </form>}
    {notice && <div className="notice-message" role="status" data-testid="status-password-recovery">{notice}</div>}
    <p className="modal-switch"><button data-testid="button-recovery-signin" disabled={busy || mode === 'checking'} onClick={backToSignIn}>Back to sign in</button></p>
  </>;
}
