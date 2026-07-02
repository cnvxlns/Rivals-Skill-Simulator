'use client';

import { useEffect, useState, useRef } from 'react';
import { checkHealth } from './api';

export type WarmupStatus = 'checking' | 'waking' | 'ready' | 'error';

export function useBackendWarmup() {
  const [status, setStatus] = useState<WarmupStatus>('checking');
  const [elapsedSeconds, setElapsedSeconds] = useState<number>(0);
  const startTimeRef = useRef<number>(Date.now());
  const timerRef = useRef<NodeJS.Timeout | null>(null);
  const pollRef = useRef<NodeJS.Timeout | null>(null);

  const cleanup = () => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
    if (pollRef.current) {
      clearTimeout(pollRef.current);
      pollRef.current = null;
    }
  };

  const startWarmup = () => {
    setStatus('checking');
    setElapsedSeconds(0);
    startTimeRef.current = Date.now();

    cleanup();

    // Timer to update elapsed seconds and handle state transitions
    timerRef.current = setInterval(() => {
      const elapsed = Date.now() - startTimeRef.current;
      const secs = Math.floor(elapsed / 1000);
      setElapsedSeconds(secs);

      // Handle soft cap (90 seconds)
      if (elapsed >= 90000) {
        setStatus('error');
        cleanup();
        return;
      }

      // Handle overlay delay transition (1200ms)
      setStatus((current) => {
        if (current === 'checking' && elapsed >= 1200) {
          return 'waking';
        }
        return current;
      });
    }, 100);

    const poll = async () => {
      try {
        const response = await checkHealth(8000);
        setStatus((current) => {
          if (current === 'ready' || current === 'error') {
            return current;
          }
          if (response && response.status === 'ok') {
            cleanup();
            return 'ready';
          }
          pollRef.current = setTimeout(poll, 2000);
          return current;
        });
      } catch (err) {
        setStatus((current) => {
          if (current === 'ready' || current === 'error') {
            return current;
          }
          pollRef.current = setTimeout(poll, 2000);
          return current;
        });
      }
    };

    poll();
  };

  const retry = () => {
    startWarmup();
  };

  useEffect(() => {
    startWarmup();
    return cleanup;
  }, []);

  return {
    status,
    elapsedSeconds,
    retry,
  };
}
