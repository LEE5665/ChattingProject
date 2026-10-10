import { useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { api, errorText, mergeMessages } from '../api'
import type { ChatRoom, Member, Message } from '../api'

type Props = {
  room: ChatRoom; member: Member; live: Message[]; epoch: number; online: boolean
  send: (roomId: number, content: string, clientId: string) => Promise<Message>
  onVoice: () => void; voiceBusy: boolean
}
export default function ChatPanel({ room, member, live, epoch, online, send, onVoice, voiceBusy }: Props) {
  const [history, setHistory] = useState<Message[]>([])
  const [loading, setLoading] = useState(true)
  const [older, setOlder] = useState(true)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const latest = useRef(0)
  const initialized = useRef(false)
  const retry = useRef<{ content: string; clientId: string } | null>(null)
  const bottom = useRef<HTMLDivElement>(null)
  const messages = useMemo(() => mergeMessages(history, live), [history, live])
  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        if (!initialized.current) {
          const batch = await api<Message[]>('/api/rooms/' + room.id + '/messages')
          if (cancelled) return
          setHistory(batch); setOlder(batch.length === 50)
          latest.current = batch.at(-1)?.id ?? 0
          initialized.current = true
        } else {
          let cursor = latest.current
          let batch: Message[]
          do {
            batch = await api<Message[]>('/api/rooms/' + room.id + '/messages?after=' + cursor)
            if (cancelled) return
            setHistory(old => mergeMessages(old, batch))
            if (batch.length) cursor = batch[batch.length - 1].id
          } while (batch.length === 50)
          latest.current = cursor
        }
        setError('')
      } catch (cause) { if (!cancelled) setError(errorText(cause)) }
      finally { if (!cancelled) setLoading(false) }
    }
    void load()
    return () => { cancelled = true }
  }, [room.id, epoch])
  const lastId = messages.at(-1)?.id
  useEffect(() => {
    if (!lastId) return
    bottom.current?.scrollIntoView({ behavior: 'smooth' })
    // Do not advance the catch-up cursor from live events: missing offline messages must still be fetched.
    api('/api/rooms/' + room.id + '/read', 'POST', { messageId: lastId }).catch(cause => setError(errorText(cause)))
  }, [room.id, lastId])
  async function loadOlder() {
    if (loadingOlder || !messages.length) return
    setLoadingOlder(true)
    try {
      const batch = await api<Message[]>('/api/rooms/' + room.id + '/messages?before=' + messages[0].id)
      setHistory(old => mergeMessages(batch, old)); setOlder(batch.length === 50)
    } catch (cause) { setError(errorText(cause)) }
    finally { setLoadingOlder(false) }
  }
  async function submit(event: FormEvent) {
    event.preventDefault()
    const content = draft.trim()
    if (!content || sending || !online) return
    const attempt = retry.current?.content === content ? retry.current : { content, clientId: crypto.randomUUID() }
    retry.current = attempt
    setSending(true); setError('')
    try {
      const saved = await send(room.id, content, attempt.clientId)
      setHistory(old => mergeMessages(old, [saved])); setDraft(''); retry.current = null
    } catch (cause) { setError(errorText(cause)) }
    finally { setSending(false) }
  }
  return <section className="chat-panel">
    <header className="chat-header"><div><h1>{room.type === 'DIRECT' ? '@' : '#'} {room.name}</h1><p>{room.members.map(m => m.nickname).join(', ')}</p></div>
      <button className="secondary" onClick={onVoice} disabled={voiceBusy}>음성 참여</button></header>
    <div className="message-list" aria-label="채팅 메시지" aria-live="polite">
      {loading && <p className="muted">대화 불러오는 중…</p>}
      {older && messages.length > 0 && <button className="secondary older" disabled={loadingOlder} onClick={loadOlder}>{loadingOlder ? '불러오는 중…' : '이전 메시지'}</button>}
      {!loading && !messages.length && <div className="empty"><h2>대화를 시작해 보세요</h2><p>이 방의 첫 메시지를 남겨보세요.</p></div>}
      {messages.map(message => <article className={'chat-message ' + (message.sender.id === member.id ? 'mine' : '')} key={message.id}>
        <span className="avatar">{message.sender.nickname[0]}</span><div><div className="message-meta"><strong>{message.sender.nickname}</strong><time>{new Date(message.createdAt).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</time></div>
          <p>{message.content}</p></div></article>)}
      <div ref={bottom} />
    </div>
    {error && <p className="error panel-error" role="alert">{error}</p>}
    <form className="composer" onSubmit={submit}>
      <input aria-label="메시지" placeholder={online ? room.name + '에 메시지 보내기' : '서버에 다시 연결하는 중…'} value={draft} onChange={e => setDraft(e.target.value)} maxLength={2000} disabled={sending} />
      <button className="primary" disabled={!online || sending || !draft.trim()}>{sending ? '전송 중' : '보내기'}</button>
    </form>
  </section>
}
