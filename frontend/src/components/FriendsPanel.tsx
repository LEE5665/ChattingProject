import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, errorText } from '../api'
import type { Friend, ChatRoom } from '../api'

type Props = { friends: Friend[]; refresh: () => Promise<void>; openRoom: (room: ChatRoom) => void }
export default function FriendsPanel({ friends, refresh, openRoom }: Props) {
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [selected, setSelected] = useState<number[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const accepted = friends.filter(f => f.status === 'ACCEPTED')
  const pending = friends.filter(f => f.status === 'PENDING')
  async function run(action: () => Promise<void>) {
    if (busy) return
    setBusy(true); setError(''); setNotice('')
    try { await action(); await refresh() }
    catch (cause) { setError(errorText(cause)) }
    finally { setBusy(false) }
  }
  function request(event: FormEvent) {
    event.preventDefault()
    void run(async () => { await api('/api/friends/requests', 'POST', { email: email.trim() }); setEmail(''); setNotice('친구 요청을 보냈어요.') })
  }
  function direct(id: number) {
    void run(async () => openRoom(await api<ChatRoom>('/api/rooms/direct', 'POST', { memberId: id })))
  }
  function group(event: FormEvent) {
    event.preventDefault()
    void run(async () => { const room = await api<ChatRoom>('/api/rooms/group', 'POST', { name: name.trim(), memberIds: selected }); setName(''); setSelected([]); openRoom(room) })
  }
  return <section className="friends-panel">
    <header className="chat-header"><div><h1>친구</h1><p>친구와 대화하거나 새로운 그룹을 만들어 보세요.</p></div></header>
    <div className="friends-content">
      <section className="surface"><h2>친구 추가</h2><form className="inline-form" onSubmit={request}>
        <input type="email" aria-label="친구 이메일" placeholder="친구의 이메일 주소" required maxLength={254} value={email} onChange={e => setEmail(e.target.value)} />
        <button className="primary" disabled={busy}>요청 보내기</button></form>
      </section>
      {error && <p className="error" role="alert">{error}</p>}{notice && <p className="notice" role="status">{notice}</p>}
      <section><h2>친구 요청 <span className="count">{pending.length}</span></h2>
        {!pending.length && <p className="muted">대기 중인 요청이 없습니다.</p>}
        {pending.map(f => <div className="friend-row" key={f.id}><span className="avatar">{f.member.nickname[0]}</span><div className="person"><strong>{f.member.nickname}</strong><span>{f.member.email}</span></div>
          {f.incoming ? <div className="actions"><button disabled={busy} className="secondary" onClick={() => void run(async () => { await api('/api/friends/requests/' + f.id + '/accept', 'POST') })}>수락</button><button disabled={busy} className="secondary" onClick={() => void run(async () => { await api('/api/friends/requests/' + f.id + '/reject', 'POST') })}>거절</button></div> : <span className="muted">수락 대기</span>}
        </div>)}
      </section>
      <section><h2>내 친구 <span className="count">{accepted.length}</span></h2>
        {!accepted.length && <p className="muted">이메일로 친구 요청을 보내보세요.</p>}
        {accepted.map(f => <div className="friend-row" key={f.id}><span className="avatar">{f.member.nickname[0]}</span><div className="person"><strong>{f.member.nickname}</strong><span>{f.member.email}</span></div><button className="secondary" disabled={busy} onClick={() => direct(f.member.id)}>개인 채팅</button></div>)}
      </section>
      <section className="surface"><h2>그룹 채팅 만들기</h2><p className="muted">친구 두 명 이상을 선택하면 나를 포함한 그룹이 만들어집니다.</p>
        <form onSubmit={group}><input aria-label="그룹 이름" placeholder="그룹 이름" maxLength={80} required value={name} onChange={e => setName(e.target.value)} />
          <div className="friend-choices">{accepted.map(f => <label className="friend-choice" key={f.id}><input type="checkbox" checked={selected.includes(f.member.id)} onChange={e => setSelected(old => e.target.checked ? [...old, f.member.id] : old.filter(id => id !== f.member.id))} />{f.member.nickname}</label>)}</div>
          <button className="primary" disabled={busy || selected.length < 2 || !name.trim()}>그룹 만들기 ({selected.length}명 선택)</button></form>
      </section>
    </div>
  </section>
}
