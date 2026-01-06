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
