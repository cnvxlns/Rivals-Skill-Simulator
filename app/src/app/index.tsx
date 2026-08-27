import React, { useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTranslation } from '@/lib/i18n';
import { useBackendWarmup } from '@/lib/useBackendWarmup';
import { useAppTheme } from '@/theme/useTheme';
import { CONTENT_MAX_WIDTH } from '@/lib/useResponsive';
import { SegmentedTabs } from '@/components/ui';
import ApkInstallButton from '@/components/ApkInstallButton';
import WakeUpOverlay from '@/components/WakeUpOverlay';
import ScoreTableView from '@/views/ScoreTableView';
import CalculatorView from '@/views/CalculatorView';
import MethodologyView from '@/views/MethodologyView';

type TabKey = 'table' | 'calculator' | 'methodology';

const DISCLAIMER =
  'This project is an unofficial fan-made application and is not affiliated with, endorsed, sponsored, or specifically approved by Com2uS Corp., MLB, or MLB Players Inc. All game data, skill names, and intellectual property are the sole property of their respective owners. This tool is intended for educational and portfolio purposes only.';

export default function HomeScreen() {
  const [tab, setTab] = useState<TabKey>('table');
  const { t } = useTranslation();
  const { status, elapsedSeconds, retry } = useBackendWarmup();
  const { colors, typography, spacing } = useAppTheme();

  // 넓은 화면에서 콘텐츠가 화면 끝까지 늘어나면 한 줄이 너무 길어 읽기 어렵고
  // 두 단어짜리 드롭다운이 1400px를 가로지른다. 본문 폭을 제한하고 가운데 정렬한다.
  const container = { width: '100%' as const, maxWidth: CONTENT_MAX_WIDTH, alignSelf: 'center' as const };

  const tabs: { key: TabKey; label: string }[] = [
    { key: 'table', label: t('tab_score_table') },
    { key: 'calculator', label: t('tab_calculator') },
    { key: 'methodology', label: t('tab_methodology') },
  ];

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <WakeUpOverlay status={status} elapsedSeconds={elapsedSeconds} onRetry={retry} />
      <SafeAreaView style={{ flex: 1 }} edges={['top']}>
        <View
          style={[container, { paddingHorizontal: spacing.lg, paddingTop: spacing.md, paddingBottom: spacing.sm }]}
        >
          <Text style={[typography.headlineSmall, { color: colors.onBackground }]}>{t('hdr_title')}</Text>
        </View>

        <View style={[container, { paddingHorizontal: spacing.lg, paddingBottom: spacing.sm, gap: spacing.sm }]}>
          <ApkInstallButton />
          <SegmentedTabs tabs={tabs} selected={tab} onSelect={setTab} />
        </View>

        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={{ padding: spacing.lg, paddingBottom: spacing.xxxl * 2, gap: spacing.md, ...container }}
          keyboardShouldPersistTaps="handled"
        >
          {tab === 'table' ? <ScoreTableView /> : null}
          {tab === 'calculator' ? <CalculatorView onViewMethodology={() => setTab('methodology')} /> : null}
          {tab === 'methodology' ? <MethodologyView /> : null}

          <Text style={[typography.bodySmall, { color: colors.muted, marginTop: spacing.xl }]}>{DISCLAIMER}</Text>
        </ScrollView>
      </SafeAreaView>
    </View>
  );
}
