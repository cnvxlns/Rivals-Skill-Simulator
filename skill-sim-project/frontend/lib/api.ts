// 백엔드 스킬 롤 API와 통신하기 위한 Axios 인스턴스 및 헬퍼 함수
import axios from 'axios';
import { RollRequest, RollResponse } from '../types';

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE_URL,
});

export async function rollSkills(payload: RollRequest): Promise<RollResponse> {
  const res = await api.post<RollResponse>('/api/skills/roll', payload);
  return res.data;
}

export default api;
