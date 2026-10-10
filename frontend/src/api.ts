export type Member = { id: number; email: string; nickname: string }
export type Friend = { id: number; member: Member; status: 'PENDING' | 'ACCEPTED'; incoming: boolean }
export type Message = { id: number; roomId: number; sender: Member; content: string; clientId: string; createdAt: string }
export type ChatRoom = { id: number; type: 'DIRECT' | 'GROUP'; name: string; members: Member[]; lastMessage: Message | null; unreadCount: number }
export type VoiceToken = { url: string; token: string; roomName: string }
export class ApiError extends Error {
  status: number
  constructor(message: string, status: number) { super(message); this.status = status }
}
export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  let encoded: BodyInit | undefined
  if (method !== 'GET') {
    const csrf = await api<{ headerName: string; token: string }>('/api/auth/csrf')
    headers[csrf.headerName] = csrf.token
    if (body instanceof URLSearchParams) encoded = body
    else if (body !== undefined) { headers['Content-Type'] = 'application/json'; encoded = JSON.stringify(body) }
  }
  const response = await fetch(path, { method, headers, body: encoded, credentials: 'include' })
  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new ApiError(error?.message ?? '요청을 처리하지 못했습니다.', response.status)
  }
  return response.status === 204 ? undefined as T : response.json()
}
export function errorText(error: unknown) {
  return error instanceof Error ? error.message : '서버에 연결하지 못했습니다.'
}
export function mergeMessages(a: Message[], b: Message[]) {
  return [...new Map([...a, ...b].map(message => [message.id, message])).values()].sort((x,y) => x.id - y.id)
}
