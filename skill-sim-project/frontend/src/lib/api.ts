// 백엔드 스킬 롤 API와 통신하기 위한 Axios 인스턴스 및 헬퍼 함수
import axios from 'axios';
import { RollRequest, RollResponse, ScoreRequest, ScoreResponse, ScoreSkillOption, MethodologyResponse } from '../types';

// NEXT_PUBLIC_API_URL이 있으면 해당 API 서버를 직접 호출한다.
// 없으면 동일 출처 /api 요청을 사용하고, next.config.js rewrites가 백엔드로 프록시한다.
const configuredApiUrl = process.env.NEXT_PUBLIC_API_URL?.trim();

export const API_BASE_URL =
  configuredApiUrl || '';

const api = axios.create({
  baseURL: API_BASE_URL,
  // 백엔드를 ngrok 무료 터널로 노출할 때 브라우저 요청에 뜨는 경고 페이지(interstitial)를
  // 건너뛴다. Vercel rewrites가 이 헤더를 백엔드로 그대로 전달한다. 같은 출처(/api) 요청이라
  // CORS preflight가 발생하지 않으며, 다른 배포(Render 등)에서는 무시되는 무해한 헤더다.
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
