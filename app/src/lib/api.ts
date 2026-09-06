// 백엔드 점수 API와 통신하기 위한 Axios 인스턴스 및 헬퍼 함수 (RN/Expo)
import axios from 'axios';
import {
  AuthResponse,
  DeckDetail,
  DeckSaveRequest,
  DeckScoreResponse,
  DeckSummary,
  AuthUser,
  ScoreRequest,
  ScoreResponse,
  ScoreSkillOption,
  MethodologyResponse,
  ScoreTableRequest,
  ScoreTableResponse,
} from '../types';

// Expo는 EXPO_PUBLIC_ 접두사 환경변수를 클라이언트 번들에 주입한다.
// 값이 없으면 상대경로(/api)를 사용한다. 네이티브에서는 반드시 절대 URL을 설정해야 한다.
const configuredApiUrl = process.env.EXPO_PUBLIC_API_URL?.trim();

export const API_BASE_URL = configuredApiUrl || '';

const api = axios.create({
  baseURL: API_BASE_URL,
  // 백엔드를 ngrok 무료 터널로 노출할 때 브라우저 경고 페이지(interstitial)를 건너뛴다.
  headers: {
    'ngrok-skip-browser-warning': 'true',
  },
});

/* ── 토큰 ────────────────────────────────────────────── */

// 웹은 Vercel, 백엔드는 개인 서버(터널)라 오리진이 다르다. 쿠키 대신 헤더로 실어
// 보내면 SameSite와 allowCredentials 문제를 겪지 않는다.
let authToken: string | null = null;

/** 토큰을 메모리에 둔다. 영속화는 AuthProvider가 AsyncStorage로 처리한다. */
export function setAuthToken(token: string | null) {
  authToken = token;
}

/** 401을 받았을 때 불린다. AuthProvider가 저장된 토큰을 지우도록 연결한다. */
let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

api.interceptors.request.use((config) => {
  if (authToken) {
    config.headers.Authorization = `Bearer ${authToken}`;
  }
  return config;
});

/**
 * 자격 증명을 확인하는 요청인가.
 *
 * 가입·로그인의 401은 "토큰이 죽었다"가 아니라 "비밀번호가 틀렸다"는 뜻이라 로그아웃
 * 대상이 아니다. 구분하지 않으면 로그인 화면에서 오타 한 번에 전역 로그아웃이 돌아
 * 다른 탭에 열어 둔 상태까지 같이 날아간다.
 *
 * `/api/auth/me`는 여기 넣지 않는다. 그쪽 401은 진짜로 토큰이 죽은 것이다.
 */
const isCredentialRequest = (url: string | undefined) =>
  (url ?? '').includes('/api/auth/login') || (url ?? '').includes('/api/auth/signup');

api.interceptors.response.use(
  (response) => response,
  (error) => {
    // 만료됐거나 서버가 재시작해 서명 키가 바뀐 경우다. 조용히 로그아웃시킨다.
    if (error?.response?.status === 401 && !isCredentialRequest(error?.config?.url)) {
      onUnauthorized?.();
    }
    return Promise.reject(error);
  },
);

/**
 * 백엔드가 내려준 오류 문구. 없으면 null.
 *
 * 백엔드는 ResponseStatusException과 빈 검증 실패에 message를 실어 준다.
 * 덱은 "SP1: Reliever role is only for relievers." 처럼 어느 자리가 왜 틀렸는지 알려주므로
 * 화면에 그대로 보여 주는 편이 낫다.
 */
export function apiErrorMessage(error: unknown): string | null {
  const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return message && message.trim() ? message : null;
}

/* ── 점수 ────────────────────────────────────────────── */

export async function fetchScoreSkills(
  cardGrade: string,
  cardVariant: string | undefined,
  position: string,
): Promise<ScoreSkillOption[]> {
  const res = await api.get<ScoreSkillOption[]>('/api/score/skills', {
    params: { cardGrade, cardVariant, position },
  });
  return res.data;
}

export async function calculateScore(payload: ScoreRequest): Promise<ScoreResponse> {
  const res = await api.post<ScoreResponse>('/api/score', payload);
  return res.data;
}

export async function getMethodology(): Promise<MethodologyResponse> {
  const res = await api.get<MethodologyResponse>('/api/score/methodology');
  return res.data;
}

export async function checkHealth(timeoutMs = 8000): Promise<{ status: string }> {
  const res = await api.get<{ status: string }>('/api/health', {
    timeout: timeoutMs,
  });
  return res.data;
}

export async function fetchScoreTable(payload: ScoreTableRequest, topN = 10): Promise<ScoreTableResponse> {
  const res = await api.post<ScoreTableResponse>(`/api/score/table?topN=${topN}`, payload);
  return res.data;
}

/* ── 인증 ────────────────────────────────────────────── */

export async function signUp(email: string, password: string): Promise<AuthResponse> {
  const res = await api.post<AuthResponse>('/api/auth/signup', { email, password });
  return res.data;
}

export async function signIn(email: string, password: string): Promise<AuthResponse> {
  const res = await api.post<AuthResponse>('/api/auth/login', { email, password });
  return res.data;
}

export async function fetchMe(): Promise<AuthUser> {
  const res = await api.get<AuthUser>('/api/auth/me');
  return res.data;
}

/* ── 덱 ──────────────────────────────────────────────── */

/** 저장하지 않고 채점만 한다. 편집 중 미리보기용이라 로그인이 필요 없다. */
export async function scoreDeck(payload: DeckSaveRequest): Promise<DeckScoreResponse> {
  const res = await api.post<DeckScoreResponse>('/api/decks/score', payload);
  return res.data;
}

export async function fetchDecks(): Promise<DeckSummary[]> {
  const res = await api.get<DeckSummary[]>('/api/decks');
  return res.data;
}

export async function fetchDeck(id: number): Promise<DeckDetail> {
  const res = await api.get<DeckDetail>(`/api/decks/${id}`);
  return res.data;
}

export async function createDeck(payload: DeckSaveRequest): Promise<DeckDetail> {
  const res = await api.post<DeckDetail>('/api/decks', payload);
  return res.data;
}

export async function updateDeck(id: number, payload: DeckSaveRequest): Promise<DeckDetail> {
  const res = await api.put<DeckDetail>(`/api/decks/${id}`, payload);
  return res.data;
}

export async function deleteDeck(id: number): Promise<void> {
  await api.delete(`/api/decks/${id}`);
}

export default api;
