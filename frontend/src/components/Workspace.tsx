import { lazy, Suspense, useCallback, useEffect, useState } from 'react'
import { api, errorText, mergeMessages } from '../api'
import type { Member, Friend, ChatRoom, Message } from '../api'
import { useChatSocket } from '../hooks/useChatSocket'
import FriendsPanel from './FriendsPanel'
import ChatPanel from './ChatPanel'
const VoicePanel = lazy(() => import('./VoicePanel'))

export default function Workspace({ member, onLogout, onExpired }: { member: Member; onLogout: () => Promise<void>; onExpired: () => void }) {
  const [friends, setFriends] = useState<Friend[]>([])
  const [rooms, setRooms] = useState<ChatRoom[]>([])
  const [selected, setSelected] = useState<number | null>(null)
  const [voice, setVoice] = useState<ChatRoom | null>(null)
  const [live, setLive] = useState<Record<number, Message[]>>({})
  const [error, setError] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)
  const refresh = useCallback(async () => {
    const [nextFriends, nextRooms] = await Promise.all([api<Friend[]>('/api/friends'), api<ChatRoom[]>('/api/rooms')])
    setFriends(nextFriends); setRooms(nextRooms)
  }, [])
  const onEvent = useCallback((event: { type: string; data: unknown }) => {
    if (event.type === 'MESSAGE' || event.type === 'ACK') {
      const message = event.data as Message
      setLive(old => ({ ...old, [message.roomId]: mergeMessages(old[message.roomId] ?? [], [message]).slice(-200) }))
    }
    if (['MESSAGE', 'FRIENDS_CHANGED', 'ROOMS_CHANGED', 'READ'].includes(event.type)) {
      refresh().catch(cause => setError(errorText(cause)))
    }
    if (event.type === 'ERROR') setError((event.data as { message: string }).message)
  }, [refresh])
  const { online, epoch, send } = useChatSocket(onEvent, onExpired)
  useEffect(() => {
    let active = true
    Promise.all([api<Friend[]>('/api/friends'), api<ChatRoom[]>('/api/rooms')])
      .then(([nextFriends, nextRooms]) => { if (active) { setFriends(nextFriends); setRooms(nextRooms) } })
      .catch(cause => { if (active) setError(errorText(cause)) })
    return () => { active = false }
  }, [epoch])
  const room = rooms.find(r => r.id === selected)
  function openRoom(next: ChatRoom) {
    setRooms(old => [next, ...old.filter(r => r.id !== next.id)])
    setSelected(next.id)
  }
  async function logout() {
    setLoggingOut(true); setError('')
    try { await onLogout() }
    catch (cause) { setError(errorText(cause)); setLoggingOut(false) }
  }
  return <main className="workspace">
    <aside className="sidebar">
      <div className="brand"><span className="brand-icon">C</span> Chatting</div>
      <p className={'connection ' + (online ? 'connected' : '')}>{online ? '실시간 연결됨' : '실시간 연결 중…'}</p>
      <button className={'nav-item ' + (selected === null ? 'active' : '')} onClick={() => setSelected(null)}>친구</button>
      <div className="channel-label">대화 <span>{rooms.length}</span></div>
      <nav className="room-nav" aria-label="채팅방 목록">{rooms.map(r => <button className={'room-item ' + (selected === r.id ? 'active' : '')} key={r.id} onClick={() => setSelected(r.id)}>
        <span className="room-symbol">{r.type === 'DIRECT' ? '@' : '#'}</span><span className="room-info"><strong>{r.name}</strong><small>{r.lastMessage?.content ?? '새로운 대화'}</small></span>{r.unreadCount > 0 && <span className="badge">{r.unreadCount}</span>}
      </button>)}</nav>
      <div className="profile"><strong>{member.nickname}</strong><span>{member.email}</span><button className="secondary" disabled={loggingOut} onClick={logout}>{loggingOut ? '처리 중…' : '로그아웃'}</button></div>
    </aside>
    <div className="main-panel">
      {error && <div className="global-error" role="alert">{error}<button onClick={() => setError('')} aria-label="알림 닫기">×</button></div>}
      {room ? <ChatPanel key={room.id} room={room} member={member} live={live[room.id] ?? []} epoch={epoch} online={online} send={send}
        onVoice={() => {
          if (voice && voice.id !== room.id) { setError('현재 음성방에서 나간 뒤 다른 방에 참여해 주세요.'); return }
          setVoice(room)
        }} voiceBusy={voice?.id === room.id} /> : <FriendsPanel friends={friends} refresh={refresh} openRoom={openRoom} />}
      {voice && <Suspense fallback={<p className="muted">음성 연결 준비 중…</p>}><VoicePanel key={voice.id} room={voice} onLeave={() => setVoice(null)} /></Suspense>}
    </div>
  </main>
}
