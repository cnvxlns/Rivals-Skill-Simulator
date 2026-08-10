import React, { useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTranslation } from '@/lib/i18n';
import { useBackendWarmup } from '@/lib/useBackendWarmup';
import { useAppTheme } from '@/theme/useTheme';
import { SegmentedTabs } from '@/components/ui';
import ApkInstallButton from '@/components/ApkInstallButton';
import WakeUpOverlay from '@/components/WakeUpOverlay';
import SimulatorView from '@/views/SimulatorView';
import CalculatorView from '@/views/CalculatorView';
import MethodologyView from '@/views/MethodologyView';

type TabKey = 'simulator' | 'calculator' | 'methodology';

const DISCLAIMER =
  'This project is an unofficial fan-made application and is not affiliated with, endorsed, sponsored, or specifically approved by Com2uS Corp., MLB, or MLB Players Inc. All game data, skill names, and intellectual property are the sole property of their respective owners. This tool is intended for educational and portfolio purposes only.';

export default function HomeScreen() {
  const [tab, setTab] = useState<TabKey>('simulator');
  const { t } = useTranslation();
  const { status, elapsedSeconds, retry } = useBackendWarmup();
  const { colors, typography, spacing } = useAppTheme();

  const tabs: { key: TabKey; label: string }[] = [
    { key: 'simulator', label: t('tab_simulator') },
    { key: 'calculator', label: t('tab_calculator') },
    { key: 'methodology', label: t('tab_methodology') },
  ];

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <WakeUpOverlay status={status} elapsedSeconds={elapsedSeconds} onRetry={retry} />
      <SafeAreaView style={{ flex: 1 }} edges={['top']}>
        <View
          style={{
            paddingHorizontal: spacing.lg,
            paddingTop: spacing.md,
            paddingBottom: spacing.sm,
          }}
        >
          <Text style={[typography.headlineSmall, { color: colors.onBackground }]}>{t('hdr_title')}</Text>
        </View>

        <View style={{ paddingHorizontal: spacing.lg, paddingBottom: spacing.sm, gap: spacing.sm }}>
          <ApkInstallButton />
          <SegmentedTabs tabs={tabs} selected={tab} onSelect={setTab} />
        </View>

        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={{ padding: spacing.lg, paddingBottom: spacing.xxxl * 2, gap: spacing.md }}
          keyboardShouldPersistTaps="handled"
        >
          {tab === 'simulator' ? <SimulatorView /> : null}
          {tab === 'calculator' ? <CalculatorView onViewMethodology={() => setTab('methodology')} /> : null}
          {tab === 'methodology' ? <MethodologyView /> : null}

          <Text style={[typography.bodySmall, { color: colors.muted, marginTop: spacing.xl }]}>{DISCLAIMER}</Text>
        </ScrollView>
      </SafeAreaView>
    </View>
  );
}
