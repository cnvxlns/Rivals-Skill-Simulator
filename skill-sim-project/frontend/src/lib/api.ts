// 백엔드 스킬 롤 API와 통신하기 위한 Axios 인스턴스 및 헬퍼 함수
import axios from 'axios';
import { RollRequest, RollResponse, ScoreRequest, ScoreResponse, ScoreSkillOption } from '../types';

// NEXT_PUBLIC_API_URL이 있으면 해당 API 서버를 직접 호출한다.
// 없으면 동일 출처 /api 요청을 사용하고, next.config.js rewrites가 백엔드로 프록시한다.
const configuredApiUrl = process.env.NEXT_PUBLIC_API_URL?.trim();

export const API_BASE_URL =
  configuredApiUrl || '';

const api = axios.create({
  baseURL: API_BASE_URL,
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

export default api;
