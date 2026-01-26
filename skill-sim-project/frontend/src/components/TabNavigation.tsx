'use client';

// Chrome-style tab navigation for toggling between primary views

type TabKey = 'simulator' | 'calculator';

type Props = {
  activeTab: TabKey;
  onTabChange: (tab: TabKey) => void;
  labels?: { simulator: string; calculator: string };
};

const TabNavigation = ({ activeTab, onTabChange, labels }: Props) => {
  const tabs: { key: TabKey; label: string }[] = [
    { key: 'simulator', label: labels?.simulator ?? 'Skill Change Simulator' },
    { key: 'calculator', label: labels?.calculator ?? 'Skill Score Calculator' },
  ];

  return (
    <nav aria-label="Primary tabs" className="relative">
      <div className="flex items-end gap-1 border-b border-slate-800/80" role="tablist">
        {tabs.map((tab) => {
          const isActive = tab.key === activeTab;
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => onTabChange(tab.key)}
              role="tab"
              aria-selected={isActive}
              className={`group relative -mb-[1px] rounded-t-2xl px-4 py-2 text-sm transition duration-150 ${
                isActive
                  ? 'bg-slate-900 text-white font-semibold shadow-[0_6px_16px_rgba(0,0,0,0.4)] border border-slate-700 border-b-slate-900'
                  : 'bg-slate-800 text-slate-300 hover:bg-slate-700 border border-slate-700/80'
              }`}
            >
              <span className="relative z-10 whitespace-nowrap">{tab.label}</span>
              {!isActive && <span className="absolute inset-x-0 bottom-0 h-[2px] bg-slate-700/80" aria-hidden />}
            </button>
          );
        })}
        <div className="flex-1" />
      </div>
    </nav>
  );
};

export type { TabKey };
export default TabNavigation;
