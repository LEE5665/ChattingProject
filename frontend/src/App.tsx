import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'

type Member = { id: number; email: string; nickname: string }
async function message(response: Response) {
  const data = await response.json().catch(() => null)
  return data?.message ?? '요청에 실패했습니다. 다시 시도해 주세요.'
}
async function me(): Promise<Member | null> {
  const response = await fetch('/api/members/me', { credentials: 'include' })
  if (response.status === 401) return null
  if (!response.ok) throw new Error(await message(response))
  return response.json()
}
async function post(path: string, body?: URLSearchParams) {
  const tokenResponse = await fetch('/api/auth/csrf', { credentials: 'include' })
  if (!tokenResponse.ok) throw new Error(await message(tokenResponse))
  const csrf: { headerName: string; token: string } = await tokenResponse.json()
  const response = await fetch(path, {
    method: 'POST', credentials: 'include',
    headers: { [csrf.headerName]: csrf.token }, body,
  })
  if (!response.ok) throw new Error(await message(response))
}
function App() {
  const [member, setMember] = useState<Member | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  useEffect(() => {
    let active = true
    me().then(user => { if (active) setMember(user) })
      .catch(() => { if (active) setError('서버에 연결할 수 없습니다. 다시 시도해 주세요.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    setBusy(true); setError('')
    try {
      await post('/api/auth/login', new URLSearchParams({ email: email.trim(), password }))
      const user = await me()
      if (!user) throw new Error('세션을 확인할 수 없습니다. 다시 로그인해 주세요.')
      setMember(user); setPassword('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '로그인에 실패했습니다.')
    } finally { setBusy(false) }
  }
  async function logout() {
    if (busy) return
    setBusy(true); setError('')
    try { await post('/api/auth/logout'); setMember(null) }
    catch (cause) { setError(cause instanceof Error ? cause.message : '로그아웃에 실패했습니다.') }
    finally { setBusy(false) }
  }
  if (loading) return <main className="login-page"><p role="status">로그인 상태 확인 중…</p></main>
  if (member) return (
    <main className="workspace">
      <aside className="sidebar">
        <div className="brand"><span className="brand-icon">C</span> Chatting</div>
        <div className="channel-label">내 공간</div><div className="selected-channel"># 시작하기</div>
        <div className="profile"><strong>{member.nickname}</strong><span>{member.email}</span></div>
      </aside>
      <section className="main-panel">
        <header><strong># 시작하기</strong><button className="secondary" disabled={busy} onClick={logout}>{busy ? '처리 중…' : '로그아웃'}</button></header>
        <div className="welcome">
          <span className="welcome-icon" aria-hidden="true">#</span>
          <h1>{member.nickname}님, 반가워요!</h1>
          <p>로그인되었습니다. 이곳에서 대화를 시작할 수 있어요.</p>
          <p className="muted">채팅 기능은 준비 중입니다.</p>
          {error && <p className="error" role="alert">{error}</p>}
        </div>
      </section>
    </main>
  )
  return (
    <main className="login-page"><section className="login-card">
      <div className="brand"><span className="brand-icon">C</span> Chatting</div>
      <h1>다시 만나서 반가워요!</h1><p className="intro">로그인하고 나만의 공간으로 들어오세요.</p>
      <form onSubmit={login}>
        <label htmlFor="email">이메일</label>
        <input id="email" type="email" autoComplete="username" required maxLength={254} value={email} onChange={e => setEmail(e.target.value)} disabled={busy} placeholder="you@example.com" />
        <label htmlFor="password">비밀번호</label>
        <input id="password" type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} disabled={busy} placeholder="비밀번호를 입력하세요" />
        {error && <p className="error" role="alert">{error}</p>}
        <button className="primary" disabled={busy}>{busy ? '로그인 중…' : '로그인'}</button>
      </form>
    </section></main>
  )
}
export default App

