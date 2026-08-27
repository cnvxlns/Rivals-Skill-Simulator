// 백엔드 스킬 롤 API와 통신하기 위한 Axios 인스턴스 및 헬퍼 함수 (RN/Expo)
import axios from 'axios';
import { RollRequest, RollResponse, ScoreRequest, ScoreResponse, ScoreSkillOption, MethodologyResponse, ScoreTableRequest, ScoreTableResponse } from '../types';

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

export async function rollSkills(payload: RollRequest): Promise<RollResponse> {
  const res = await api.post<RollResponse>('/api/skills/roll', payload);
  return res.data;
}

export async function fetchInitialSkills(cardType: string, position: string, subPosition?: string | null): Promise<RollResponse> {
  const res = await api.get<RollResponse>('/api/skills/initial', {
    params: { cardType, position, subPosition: subPosition || undefined },
  });
  return res.data;
}

export async function fetchScoreSkills(cardType: string, position: string): Promise<ScoreSkillOption[]> {
  const res = await api.get<ScoreSkillOption[]>('/api/score/skills', {
    params: { cardType, position },
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

export default api;

export async function fetchScoreTable(payload: ScoreTableRequest, topN = 10): Promise<ScoreTableResponse> {
  const res = await api.post<ScoreTableResponse>(`/api/score/table?topN=${topN}`, payload);
  return res.data;
}
