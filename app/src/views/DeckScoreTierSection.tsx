'use client';

import { useMemo } from 'react';
import { Pressable, Text, View } from 'react-native';
import { CollapsibleSection, LabeledDropdown } from '../components/ui';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';
import { DeckRuleEffect, DeckRules, DeckScoreChoice, DeckScoreLadder, DeckScoreSide } from '../types';

/** 보상 대상을 사람이 읽는 말로. 자리 목록은 그대로 보여 준다. */
const TARGET_LABELS: Record<string, string> = {
  BATTER_ALL: '타자 전체',
  PITCHER_ALL: '투수 전체',
  SP: '선발',
  RP: '중계',
  RP_CP: '중계·마무리',
  CP: '마무리',
};

/** 조건을 사람이 읽는 말로. 빈 문자열이면 조건 없이 늘 걸린다. */
const CONDITION_LABELS: Record<string, string> = {
  ALWAYS: '',
  CARD_LIVE_SEASON: '라이브·시즌 카드',
  CARD_NOT_LIVE_SEASON: '그 외 카드',
  ORDER_1_2: '1~2번 타순',
  ORDER_3_5: '3~5번 타순',
  ORDER_6_9: '6~9번 타순',
  ENHANCE_GTE_10: '강화 10 이상',
  DECADE: '고른 연대의 카드',
};

const describe = (effects: DeckRuleEffect[]): string => {
  if (!effects.length) return '효과 없음';
  // 같은 대상·조건끼리 묶어 "타자 전체 파워+3 정확+3"처럼 한 줄로 만든다.
  const groups = new Map<string, string[]>();
  effects.forEach((effect) => {
    const target = TARGET_LABELS[effect.target] ?? effect.target;
    const condition = CONDITION_LABELS[effect.condition] ?? effect.condition;
    const key = condition ? `${target} · ${condition}` : target;
    groups.set(key, [...(groups.get(key) ?? []), `${effect.stat}+${effect.amount}`]);
  });
  return [...groups].map(([key, stats]) => `${key} ${stats.join(' ')}`).join(' / ');
};

/**
 * 덱 스코어 보상 사다리. 임계값마다 좌·우 중 하나를 고른다.
 *
 * 게임은 총합으로 자동 해금해 주지만 그 총합이 무엇의 합인지 확인되지 않아, 워크북과 같이
 * 직접 고르게 둔다. 임계값을 그대로 보여 주므로 게임 화면과 나란히 놓고 옮겨 적을 수 있다.
 *
 * 연대 칸(스페셜 615·645·680)만 연도를 함께 고른다. 그 카드의 연도가 `[연대, 연대+9]`
 * 안이면 보상을 받는다.
 */
export default function DeckScoreTierSection({
  ladder,
  rules,
  chosen,
  onChoose,
  onClear,
}: {
  ladder: DeckScoreLadder;
  rules: DeckRules | null;
  chosen: Record<string, DeckScoreChoice>;
  onChoose: (ladder: DeckScoreLadder, threshold: number, side: DeckScoreSide, decadeYear?: number | null) => void;
  onClear: (ladder: DeckScoreLadder, threshold: number) => void;
}) {
  const { t } = useTranslation();
  const { colors, radius, spacing, typography } = useAppTheme();

  const tiers = useMemo(
    () => rules?.ladders.find((entry) => entry.ladder === ladder)?.tiers ?? [],
    [rules, ladder],
  );
  const pickedCount = tiers.filter((tier) => chosen[`${ladder}:${tier.threshold}`]).length;

  const sideButton = (
    threshold: number,
    side: DeckScoreSide,
    effects: DeckRuleEffect[],
    decadeYear: number | null,
  ) => {
    const current = chosen[`${ladder}:${threshold}`];
    const active = current?.side === side;
    return (
      <Pressable
        onPress={() => onChoose(ladder, threshold, side, decadeYear)}
        accessibilityRole="button"
        accessibilityState={{ selected: active }}
        style={{
          flex: 1,
          borderWidth: 1,
          borderColor: active ? colors.accentAction : colors.outline,
          backgroundColor: active ? colors.surfaceVariant : 'transparent',
          borderRadius: radius.control,
          paddingVertical: spacing.sm,
          paddingHorizontal: spacing.md,
          gap: 2,
        }}
      >
        <Text style={{ ...typography.label, color: active ? colors.accentAction : colors.secondaryText }}>
          {side === 'LEFT' ? t('deck_coord_left') : t('deck_coord_right')}
          {active ? ' ✓' : ''}
        </Text>
        <Text style={{ ...typography.caption, color: colors.muted }}>{describe(effects)}</Text>
      </Pressable>
    );
  };

  return (
    <CollapsibleSection
      title={ladder === 'TEAM' ? t('deck_coord_team') : t('deck_coord_special')}
      summary={`${pickedCount}/${tiers.length}`}
    >
      <View style={{ gap: spacing.md }}>
        <Text style={{ ...typography.label, color: colors.muted }}>{t('deck_coord_hint')}</Text>
        {tiers.map((tier) => {
          const key = `${ladder}:${tier.threshold}`;
          const current = chosen[key];
          const decadeYear = current?.decadeYear ?? rules?.decadeYears?.[rules.decadeYears.length - 1] ?? null;
          return (
            <View key={key} style={{ gap: spacing.xs }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
                <Text style={{ ...typography.label, color: colors.onSurface, width: 56 }}>
                  {tier.threshold}
                </Text>
                {tier.decade ? (
                  <View style={{ flex: 1 }}>
                    <LabeledDropdown
                      label={t('deck_coord_decade')}
                      selected={decadeYear ?? 0}
                      options={rules?.decadeYears ?? []}
                      optionLabel={(year) => `${year}년대`}
                      onSelect={(year) =>
                        onChoose(ladder, tier.threshold, current?.side ?? 'LEFT', year)
                      }
                    />
                  </View>
                ) : null}
                {current ? (
                  <Pressable onPress={() => onClear(ladder, tier.threshold)} accessibilityRole="button">
                    <Text style={{ ...typography.label, color: colors.muted }}>
                      {t('deck_coord_clear')}
                    </Text>
                  </Pressable>
                ) : null}
              </View>
              <View style={{ flexDirection: 'row', gap: spacing.sm }}>
                {sideButton(tier.threshold, 'LEFT', tier.left, tier.decade ? decadeYear : null)}
                {sideButton(tier.threshold, 'RIGHT', tier.right, tier.decade ? decadeYear : null)}
              </View>
            </View>
          );
        })}
      </View>
    </CollapsibleSection>
  );
}
