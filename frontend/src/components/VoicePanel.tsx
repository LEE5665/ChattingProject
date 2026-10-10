import { useCallback, useEffect, useRef, useState } from 'react'
import { AudioPresets, Room, RoomEvent, Track } from 'livekit-client'
import type { Participant } from 'livekit-client'
import { api, errorText } from '../api'
import type { ChatRoom, VoiceToken } from '../api'

type Person = { identity: string; name: string; muted: boolean; speaking: boolean }
export default function VoicePanel({ room, onLeave }: { room: ChatRoom; onLeave: () => void }) {
  const connection = useRef<Room | null>(null)
  const audio = useRef<HTMLDivElement>(null)
  const [status, setStatus] = useState('연결 중…')
  const [error, setError] = useState('')
  const [people, setPeople] = useState<Person[]>([])
  const [muted, setMuted] = useState(false)
  const [devices, setDevices] = useState<MediaDeviceInfo[]>([])
  const [deviceId, setDeviceId] = useState('')
  const [busy, setBusy] = useState(false)
  const refresh = useCallback(() => {
    const active = connection.current
    if (!active) return
    const participants: Participant[] = [active.localParticipant, ...active.remoteParticipants.values()]
    setPeople(participants.map(p => ({ identity: p.identity, name: p.name || p.identity, muted: !p.isMicrophoneEnabled, speaking: p.isSpeaking })))
    setMuted(!active.localParticipant.isMicrophoneEnabled)
  }, [])
  useEffect(() => {
    let cancelled = false
    const active = new Room({
      audioCaptureDefaults: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      publishDefaults: { audioPreset: AudioPresets.musicHighQuality },
    })
    connection.current = active
    const container = audio.current
    const showError = (cause: unknown) => { if (!cancelled) setError(errorText(cause)) }
    active.on(RoomEvent.TrackSubscribed, track => {
      if (track.kind === Track.Kind.Audio && !cancelled) {
        const element = track.attach()
        element.autoplay = true
        container?.appendChild(element)
        void element.play().catch(() => showError(new Error('소리 재생 버튼을 눌러 음성을 허용해 주세요.')))
      }
    })
    active.on(RoomEvent.TrackUnsubscribed, track => track.detach().forEach(element => element.remove()))
    active.on(RoomEvent.ParticipantConnected, refresh)
    active.on(RoomEvent.ParticipantDisconnected, refresh)
    active.on(RoomEvent.ActiveSpeakersChanged, refresh)
    active.on(RoomEvent.TrackMuted, refresh)
    active.on(RoomEvent.TrackUnmuted, refresh)
    active.on(RoomEvent.LocalTrackPublished, refresh)
    active.on(RoomEvent.Reconnecting, () => { if (!cancelled) setStatus('다시 연결 중…') })
    active.on(RoomEvent.Reconnected, () => { if (!cancelled) { setStatus('음성 연결됨'); refresh() } })
    active.on(RoomEvent.Disconnected, () => {
      if (!cancelled) { setStatus('연결 종료'); setError('음성 연결이 종료되었습니다. 나간 뒤 다시 참여해 주세요.'); setPeople([]) }
    })
    async function join() {
      try {
        const token = await api<VoiceToken>('/api/rooms/' + room.id + '/voice-token', 'POST')
        if (cancelled) return
        await active.connect(token.url, token.token)
        if (cancelled) { await active.disconnect(); return }
        await active.localParticipant.setMicrophoneEnabled(true)
        if (cancelled) { await active.disconnect(); return }
        setStatus('음성 연결됨'); refresh()
        const microphones = await Room.getLocalDevices('audioinput')
        if (!cancelled) { setDevices(microphones); setDeviceId(active.getActiveDevice('audioinput') ?? '') }
      } catch (cause) {
        if (!cancelled) { setStatus('연결 실패'); showError(cause) }
        await active.disconnect()
      }
    }
    void join()
    return () => {
      cancelled = true
      connection.current = null
      active.removeAllListeners()
      void active.disconnect()
      container?.replaceChildren()
    }
  }, [room.id, refresh])
  async function toggleMute() {
    const active = connection.current
    if (!active || busy || status !== '음성 연결됨') return
    setBusy(true)
    try { await active.localParticipant.setMicrophoneEnabled(muted); refresh(); setError('') }
    catch (cause) { setError(errorText(cause)) }
    finally { setBusy(false) }
  }
  async function switchMicrophone(id: string) {
    const active = connection.current
    if (!active) return
    setBusy(true)
    try { await active.switchActiveDevice('audioinput', id); setDeviceId(id); setError('') }
    catch (cause) { setError(errorText(cause)) }
    finally { setBusy(false) }
  }
  return <section className="voice-panel" aria-label="음성 채팅">
    <div className="voice-heading"><div><strong>{room.name}</strong><span className="voice-status">{status}</span></div><button className="danger" onClick={onLeave}>나가기</button></div>
    <div className="voice-people">{people.map(p => <span className={'voice-person ' + (p.speaking ? 'speaking' : '')} key={p.identity}>{p.name} {p.muted ? '(음소거)' : ''}</span>)}</div>
    <div className="voice-controls"><button className="secondary" onClick={toggleMute} disabled={busy || status !== '음성 연결됨'}>{muted ? '마이크 켜기' : '음소거'}</button>
      <button className="secondary" onClick={() => void connection.current?.startAudio().then(() => setError('')).catch(cause => setError(errorText(cause)))}>소리 재생</button>
      <select aria-label="마이크 선택" value={deviceId} disabled={busy || status !== '음성 연결됨'} onChange={e => void switchMicrophone(e.target.value)}>
        <option value="">기본 마이크</option>{devices.filter(d => d.deviceId).map(d => <option value={d.deviceId} key={d.deviceId}>{d.label || '마이크'}</option>)}
      </select>
    </div>
    {error && <p role="alert" className="error">{error}</p>}<div ref={audio} />
  </section>
}
