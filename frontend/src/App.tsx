import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError, errorText } from './api'
import type { Member } from './api'
import Workspace from './components/Workspace'
import './App.css'

function App() {
  const [member, setMember] = useState<Member | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  useEffect(() => {
    let active = true
    api<Member>('/api/members/me').then(user => { if (active) setMember(user) })
      .catch(cause => {
        if (active && !(cause instanceof ApiError && cause.status === 401)) setError(errorText(cause))
      }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    setBusy(true); setError('')
    try {
      await api('/api/auth/login', 'POST', new URLSearchParams({ email: email.trim(), password }))
      setMember(await api<Member>('/api/members/me')); setPassword('')
    } catch (cause) { setError(errorText(cause)) }
    finally { setBusy(false) }
  }
  async function logout() {
    await api('/api/auth/logout', 'POST')
    setMember(null)
  }
  if (loading) return <main className="login-page"><p role="status">로그인 상태 확인 중…</p></main>
  if (member) return <Workspace member={member} onLogout={logout} onExpired={() => { setMember(null); setError('로그인 세션이 만료되었습니다.') }} />
  return <main className="login-page"><section className="login-card">
    <div className="brand"><span className="brand-icon">C</span> Chatting</div>
    <h1>다시 만나서 반가워요!</h1><p className="intro">친구들과 함께 대화하고, 목소리로 연결하세요.</p>
    <form onSubmit={login}>
      <label htmlFor="email">이메일</label>
      <input id="email" type="email" autoComplete="username" required maxLength={254} value={email} onChange={e => setEmail(e.target.value)} disabled={busy} placeholder="you@example.com" />
      <label htmlFor="password">비밀번호</label>
      <input id="password" type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} disabled={busy} placeholder="비밀번호를 입력하세요" />
      {error && <p className="error" role="alert">{error}</p>}
      <button className="primary" disabled={busy}>{busy ? '로그인 중…' : '로그인'}</button>
    </form>
  </section></main>
}
export default App

