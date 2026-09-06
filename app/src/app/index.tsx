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
import DeckView from '@/views/DeckView';

type TabKey = 'table' | 'calculator' | 'deck' | 'methodology';

/** 안 보이는 탭. 상태는 살려 두고 화면에서만 뺀다. */
const HIDDEN = { display: 'none' } as const;

export default function HomeScreen() {
  const [tab, setTab] = useState<TabKey>('table');
  /**
   * 한 번이라도 연 탭. 여기 담긴 탭은 다시 언마운트하지 않는다.
   *
   * 예전에는 비활성 탭을 통째로 지웠는데, 그러면 덱을 편집하다 계산기를 잠깐 보고
   * 돌아오면 편집하던 내용이 사라졌다. 덱 탭은 마운트될 때마다 목록을 다시 받아서
   * 토큰이 죽어 있으면 그 요청이 401을 물고 와 로그아웃까지 됐다.
   *
   * 처음부터 넷을 다 켜 두지는 않는다. 그러면 첫 화면에서 쓰지도 않을 요청이 세 개
   * 더 나간다. 열어 본 탭만 남긴다.
   */
  const [visited, setVisited] = useState<TabKey[]>(['table']);
  const openTab = (next: TabKey) => {
    setTab(next);
    setVisited((prev) => (prev.includes(next) ? prev : [...prev, next]));
  };
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
    { key: 'deck', label: t('tab_deck') },
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
                <AppMark size={isWide ? 30 : 26} />
                <Text style={[typography.section, { color: colors.onSurface }]} numberOfLines={1}>
                  {t(isWide ? 'hdr_title' : 'hdr_title_short')}
                </Text>
              </View>
            </View>

            <SegmentedTabs tabs={tabs} selected={tab} onSelect={openTab} stretch={!isWide} />
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
            {/*
              열어 본 탭은 지우지 않고 숨긴다. display가 'none'이면 레이아웃에서 빠지므로
              contentContainerStyle의 gap이 빈 칸으로 남지도 않는다.
            */}
            {visited.includes('table') ? (
              <View style={tab === 'table' ? undefined : HIDDEN}>
                <ScoreTableView />
              </View>
            ) : null}
            {visited.includes('calculator') ? (
              <View style={tab === 'calculator' ? undefined : HIDDEN}>
                <CalculatorView onViewMethodology={() => openTab('methodology')} />
              </View>
            ) : null}
            {visited.includes('deck') ? (
              <View style={tab === 'deck' ? undefined : HIDDEN}>
                <DeckView />
              </View>
            ) : null}
            {visited.includes('methodology') ? (
              <View style={tab === 'methodology' ? undefined : HIDDEN}>
                <MethodologyView />
              </View>
            ) : null}

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
                      fontSize: 15.5,
                      lineHeight: 20.125,
                    }}
                  >
                    {t('disclaimer_full')}
                  </Text>
                  <View style={{ width: 320 }}>
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
                      fontSize: 15.5,
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
