// 백엔드 스킬 롤 API와 통신하기 위한 Axios 인스턴스 및 헬퍼 함수
import axios from 'axios';
import { RollRequest, RollResponse } from '../types';

const api = axios.create({
    baseURL: 'http://localhost:8080',
});

export async function rollSkills(payload: RollRequest): Promise<RollResponse> {
    const res = await api.post<RollResponse>('/api/skills/roll', payload);
    return res.data;
}

export default api;
