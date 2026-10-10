import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiError } from '../api'
import type { Message } from '../api'

type Event = { type: string; data: unknown }
type Pending = { resolve: (message: Message) => void; reject: (error: Error) => void; timer: ReturnType<typeof setTimeout> }
export function useChatSocket(onEvent: (event: Event) => void, onExpired: () => void) {
  const socket = useRef<WebSocket | null>(null)
  const callback = useRef(onEvent)
  const expired = useRef(onExpired)
  const pending = useRef(new Map<string, Pending>())
  const [online, setOnline] = useState(false)
  const [epoch, setEpoch] = useState(0)
  useEffect(() => { callback.current = onEvent; expired.current = onExpired }, [onEvent, onExpired])
  useEffect(() => {
    let cancelled = false
    let reconnect: ReturnType<typeof setTimeout>
    let heartbeat: ReturnType<typeof setInterval>
    const requests = pending.current
    function rejectPending(reason: string) {
      requests.forEach(p => { clearTimeout(p.timer); p.reject(new Error(reason)) }); requests.clear()
    }
    async function connect() {
      try { await api('/api/members/me') }
      catch (cause) {
        if (cancelled) return
        if (cause instanceof ApiError && cause.status === 401) { expired.current(); return }
        reconnect = setTimeout(() => void connect(), 2000); return
      }
      if (cancelled) return
      const ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws/chat')
      socket.current = ws
      ws.onmessage = event => {
        if (cancelled) return
        const message: Event = JSON.parse(event.data)
        if (message.type === 'READY') { setOnline(true); setEpoch(old => old + 1) }
        if (message.type === 'ACK') {
          const saved = message.data as Message
          const p = requests.get(saved.clientId)
          if (p) { clearTimeout(p.timer); p.resolve(saved); requests.delete(saved.clientId) }
        }
        if (message.type === 'ERROR') rejectPending((message.data as { message: string }).message)
        callback.current(message)
      }
      ws.onclose = () => {
        clearInterval(heartbeat)
        if (cancelled) return
        setOnline(false); rejectPending('연결이 끊겼습니다. 같은 메시지를 다시 전송할 수 있습니다.')
        reconnect = setTimeout(() => void connect(), 2000)
      }
      heartbeat = setInterval(() => { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ type: 'PING' })) }, 20000)
    }
    void connect()
    return () => {
      cancelled = true; clearTimeout(reconnect); clearInterval(heartbeat)
      socket.current?.close(); rejectPending('연결이 종료되었습니다.')
    }
  }, [])
  const send = useCallback((roomId: number, content: string, clientId: string): Promise<Message> => {
    return new Promise((resolve, reject) => {
      const ws = socket.current
      if (ws?.readyState !== WebSocket.OPEN) { reject(new Error('서버 연결을 기다려 주세요.')); return }
      const timer = setTimeout(() => { pending.current.delete(clientId); reject(new Error('전송 확인이 지연되고 있습니다. 다시 시도해 주세요.')) }, 10000)
      pending.current.set(clientId, { resolve, reject, timer })
      ws.send(JSON.stringify({ type: 'SEND_MESSAGE', data: { roomId, content, clientId } }))
    })
  }, [])
  return { online, epoch, send }
}
