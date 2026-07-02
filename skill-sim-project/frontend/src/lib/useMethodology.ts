'use client';

import { useEffect, useState } from 'react';
import { getMethodology } from './api';
import { MethodologyResponse } from '../types';

export function useMethodology() {
  const [data, setData] = useState<MethodologyResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchMethodology = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await getMethodology();
      setData(res);
    } catch (err: any) {
      console.error('Error fetching methodology:', err);
      setError(err?.response?.data?.message || err.message || 'Failed to load methodology');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMethodology();
  }, []);

  return {
    data,
    loading,
    error,
    refresh: fetchMethodology,
  };
}
