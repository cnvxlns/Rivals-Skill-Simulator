import React, { useState } from 'react';
import Head from 'expo-router/head';
import { ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTranslation } from '@/lib/i18n';
import { useBackendWarmup } from '@/lib/useBackendWarmup';
import { useAppTheme } from '@/theme/useTheme';
import { GUTTER, useResponsive } from '@/lib/useResponsive';
import { SegmentedTabs, TooltipProvider } from '@/components/ui';
import { AppMark } from '@/components/icons';
import ApkInstallButton from '@/components/ApkInstallButton';
import WakeUpOverlay from '@/components/WakeUpOverlay';
import ScoreTableView from '@/views/ScoreTableView';
import CalculatorView from '@/views/CalculatorView';
import MethodologyView from '@/views/MethodologyView';

type TabKey = 'table' | 'calculator' | 'methodology';

export default function HomeScreen() {
  const [tab, setTab] = useState<TabKey>('table');
  const { t } = useTranslation();
  const { status, elapsedSeconds, retry } = useBackendWarmup();
  const { colors, typography, spacing } = useAppTheme();
  const { isWide } = useResponsive();

  // 폭 상한을 두지 않는다. 넓어진 폭은 한 줄을 늘리는 데 쓰지 않고 그리드 열 수를
  // 늘려 흡수한다(columnsFor). 좌우에는 GUTTER만 둔다.
  const container = { width: '100%' as const };

  const tabs: { key: TabKey; label: string }[] = [
    { key: 'table', label: t('tab_score_table') },
    { key: 'calculator', label: t('tab_calculator') },
    { key: 'methodology', label: t('tab_methodology') },
  ];

  const horizontalPadding = isWide ? GUTTER : spacing.lg;

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      {/*
        제목은 여기서만 넣는다. +html.tsx에 <title>을 직접 쓰면 expo-router가 helmet으로
        먼저 심는 빈 <title data-rh>와 중복되고, 브라우저는 앞선 빈 것을 쓴다.
        Head는 웹에서만 의미가 있고 네이티브에서는 무시된다.
      */}
      <Head>
        <title>라이벌 전력분석실 — MLB 라이벌 스킬 점수</title>
      </Head>
      <WakeUpOverlay status={status} elapsedSeconds={elapsedSeconds} onRetry={retry} />
      {/* 툴팁 오버레이는 카드의 overflow:'hidden' 밖, 화면 최상단에 떠야 한다. */}
      <TooltipProvider>
        <SafeAreaView style={{ flex: 1 }} edges={['top']}>
          <View
            style={[
              container,
              {
                paddingHorizontal: horizontalPadding,
                paddingTop: isWide ? spacing.xxlx : spacing.lg,
                paddingBottom: spacing.xl,
                gap: spacing.xl,
              },
            ]}
          >
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: spacing.md,
              }}
            >
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md, flexShrink: 1 }}>
                <AppMark size={isWide ? 26 : 22} />
                <Text style={[typography.section, { color: colors.onSurface }]} numberOfLines={1}>
                  {t(isWide ? 'hdr_title' : 'hdr_title_short')}
                </Text>
              </View>
            </View>

            <SegmentedTabs tabs={tabs} selected={tab} onSelect={setTab} stretch={!isWide} />
          </View>

          <ScrollView
            style={{ flex: 1 }}
            contentContainerStyle={{
              paddingHorizontal: horizontalPadding,
              paddingTop: spacing.md,
              paddingBottom: spacing.huge,
              gap: spacing.md,
              ...container,
            }}
            keyboardShouldPersistTaps="handled"
          >
            {tab === 'table' ? <ScoreTableView /> : null}
            {tab === 'calculator' ? <CalculatorView onViewMethodology={() => setTab('methodology')} /> : null}
            {tab === 'methodology' ? <MethodologyView /> : null}

            <View
              style={{
                borderTopWidth: 1,
                borderTopColor: colors.divider,
                paddingTop: spacing.xxlx,
                marginTop: spacing.xxxl,
                flexDirection: isWide ? 'row' : 'column',
                gap: isWide ? spacing.huge : spacing.xl,
                alignItems: 'flex-start',
              }}
            >
              {isWide ? (
                <>
                  <Text
                    style={{
                      flex: 1,
                      color: colors.mutedFaint,
                      fontSize: 13.5,
                      lineHeight: 20.125,
                    }}
                  >
                    {t('disclaimer_full')}
                  </Text>
                  <View style={{ width: 280 }}>
                    <ApkInstallButton />
                  </View>
                </>
              ) : (
                <>
                  <View style={{ width: '100%' }}>
                    <ApkInstallButton />
                  </View>
                  <Text
                    style={{
                      color: colors.mutedFaint,
                      fontSize: 13.5,
                      lineHeight: 20.125,
                    }}
                  >
                    {t('disclaimer_short')}
                  </Text>
                </>
              )}
            </View>
          </ScrollView>
        </SafeAreaView>
      </TooltipProvider>
    </View>
  );
}
