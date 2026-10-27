import { useState } from 'react'
import { Activity, LogIn, ShieldCheck, UserPlus } from 'lucide-react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { getApiErrorMessage } from '../services/api.js'

export default function LoginPage() {
  const { user, login, register, registrationEnabled } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [mode, setMode] = useState('login')
  const [values, setValues] = useState({ username: '', password: '', displayName: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const destination = location.state?.from || '/dashboard'

  if (user) return <Navigate to={destination} replace />

  function update(event) {
    setValues((current) => ({ ...current, [event.target.name]: event.target.value }))
  }

  async function submit(event) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (mode === 'login') await login(values.username.trim(), values.password)
      else await register({ username: values.username.trim(), password: values.password, displayName: values.displayName.trim() || null })
      navigate(destination, { replace: true })
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, mode === 'login' ? 'Sign-in failed.' : 'Registration failed.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-shell">
      <section className="auth-card">
        <div className="brand-lockup auth-brand"><span className="brand-mark"><Activity size={20} strokeWidth={2.5} /></span><span className="brand-name">impact<span>IQ</span></span></div>
        <span className="eyebrow">AI DEFECT INVESTIGATION</span>
        <h1>{mode === 'login' ? 'Sign in to your workspace' : 'Create your account'}</h1>
        <p>Investigate defects with evidence-backed, multi-agent analysis. Impact scores stay deterministic.</p>
        <form onSubmit={submit} className="auth-form">
          <label className="form-field"><span>Username</span><input name="username" value={values.username} onChange={update} autoComplete="username" required minLength={3} maxLength={64} /></label>
          {mode === 'register' && <label className="form-field"><span>Display name</span><input name="displayName" value={values.displayName} onChange={update} maxLength={120} /></label>}
          <label className="form-field"><span>Password</span><input name="password" type="password" value={values.password} onChange={update} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={mode === 'register' ? 8 : 1} maxLength={72} /></label>
          {mode === 'register' && <small className="auth-hint">8–72 characters. The first account created becomes the administrator.</small>}
          {error && <div className="form-error" role="alert">{error}</div>}
          <button className="button button-primary" type="submit" disabled={busy}>
            {mode === 'login' ? <LogIn size={16} /> : <UserPlus size={16} />}
            {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>
        {registrationEnabled && (
          <button className="auth-switch" type="button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }}>
            {mode === 'login' ? 'New here? Create an account' : 'Already have an account? Sign in'}
          </button>
        )}
        <div className="auth-foot"><ShieldCheck size={14} />Session cookies with CSRF protection. Project data is visible to project members only.</div>
      </section>
    </div>
  )
}
