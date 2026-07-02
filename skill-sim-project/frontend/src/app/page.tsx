'use client';

import { useState } from 'react';
import LanguageSelector from '../components/LanguageSelector';
import TabNavigation, { TabKey } from '../components/TabNavigation';
import CalculatorView from '../views/CalculatorView';
import SimulatorView from '../views/SimulatorView';
import MethodologyView from '../views/MethodologyView';
import { useTranslation } from '../lib/i18n';
import { useBackendWarmup } from '../lib/useBackendWarmup';
import WakeUpOverlay from '../components/WakeUpOverlay';

export default function Page() {
  const [activeTab, setActiveTab] = useState<TabKey>('simulator');
  const { t } = useTranslation();
  const { status, elapsedSeconds, retry } = useBackendWarmup();

  return (
    <main className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-indigo-950 text-slate-50">
      <WakeUpOverlay status={status} elapsedSeconds={elapsedSeconds} onRetry={retry} />
      
      <div className="mx-auto max-w-6xl px-6 py-10">
        <div className="rounded-3xl border border-slate-800/60 bg-slate-900/40 shadow-[0_18px_40px_rgba(0,0,0,0.45)] backdrop-blur">
          <div className="flex items-center justify-between px-3 pt-3">
            <TabNavigation
              activeTab={activeTab}
              onTabChange={setActiveTab}
              labels={{
                simulator: t('tab_simulator'),
                calculator: t('tab_calculator'),
                methodology: t('tab_methodology'),
              }}
            />
            <LanguageSelector />
          </div>
          <div className="p-6">
            {activeTab === 'simulator' && <SimulatorView />}
            {activeTab === 'calculator' && <CalculatorView onViewMethodology={() => setActiveTab('methodology')} />}
            {activeTab === 'methodology' && <MethodologyView />}
          </div>
        </div>
      </div>

      <footer className="border-t border-white/10 bg-slate-950/80 text-slate-200">
        <div className="mx-auto max-w-6xl px-6 py-6 text-xs leading-relaxed">
          This project is an unofficial fan-made application and is not affiliated with, endorsed, sponsored, or specifically approved by Com2uS Corp., MLB, or MLB Players Inc. All game data, skill names, and intellectual property are the sole property of their respective owners. This tool is intended for educational and portfolio purposes only.
        </div>
      </footer>
    </main>
  );
}
