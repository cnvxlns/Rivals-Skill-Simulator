'use client';

import React from 'react';
import { useTranslation } from '../lib/i18n';
import { WarmupStatus } from '../lib/useBackendWarmup';

interface WakeUpOverlayProps {
  status: WarmupStatus;
  elapsedSeconds: number;
  onRetry: () => void;
}

export default function WakeUpOverlay({ status, elapsedSeconds, onRetry }: WakeUpOverlayProps) {
  const { t } = useTranslation();

  if (status === 'checking' || status === 'ready') {
    return null;
  }

  const isError = status === 'error';

  return (
    <div className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-slate-950/95 p-6 text-center text-slate-50 backdrop-blur-md">
      <div className="w-full max-w-md rounded-3xl border border-slate-855/60 bg-slate-900/40 p-8 shadow-[0_20px_50px_rgba(0,0,0,0.5)] backdrop-blur">
        {!isError ? (
          <div className="flex flex-col items-center">
            {/* Spinning Indicator */}
            <div className="relative mb-6 flex h-16 w-16 items-center justify-center">
              <div className="absolute inset-0 rounded-full border-4 border-indigo-500/20"></div>
              <div className="absolute inset-0 animate-spin rounded-full border-4 border-indigo-500 border-t-transparent"></div>
            </div>

            <h2 className="mb-3 text-lg font-bold text-slate-100">
              {t('warmup_waking')}
            </h2>
            <p className="mb-6 text-xs text-slate-400 leading-relaxed">
              {t('warmup_sub_waking')}
            </p>
            <div className="inline-flex items-center gap-1.5 rounded-full bg-slate-800/60 px-3.5 py-1.5 text-xs text-indigo-300">
              <span className="font-semibold">{t('warmup_elapsed')}:</span>
              <span>{elapsedSeconds}s</span>
            </div>
          </div>
        ) : (
          <div className="flex flex-col items-center">
            <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-rose-500/10 text-rose-500">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="h-8 w-8">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
              </svg>
            </div>

            <h2 className="mb-3 text-lg font-bold text-slate-100">
              {t('warmup_error')}
            </h2>
            
            <button
              onClick={onRetry}
              className="mt-4 flex items-center justify-center gap-2 rounded-xl bg-indigo-600 px-6 py-2.5 text-sm font-semibold text-white transition-all hover:bg-indigo-500 active:scale-95"
            >
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="h-4 w-4">
                <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182m0-4.991v4.99" />
              </svg>
              {t('warmup_retry')}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
