'use client';

import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { RELIEVER_ROLE_KEYS } from '../components/BullpenRoleLane';
import { SectionCard, SegmentedTabs } from '../components/ui';
import { cardTypeLabel } from '../lib/format';
import { useTranslation } from '../lib/i18n';
import { isLineup } from '../lib/useDeckEditor';
import { useAppTheme } from '../theme/useTheme';
import { DeckPlayerScore } from '../types';

const FILTER_LABELS = {
  ALL: 'deck_ranking_all',
  BATTERS: 'deck_ranking_batters',
  STARTERS: 'deck_ranking_starters',
  BULLPEN: 'deck_ranking_bullpen',
} as const;

type Filter = keyof typeof FILTER_LABELS;

function playerGroup(slot: string): Exclude<Filter, 'ALL'> | undefined {
  if (isLineup(slot)) return 'BATTERS';
  if (slot.startsWith('SP')) return 'STARTERS';
  if (/^(RP|CP)/.test(slot)) return 'BULLPEN';
}

export default function DeckPlayerRankingSection({
  players,
  onPress,
}: {
  players: readonly DeckPlayerScore[];
  onPress: (slot: string) => void;
}) {
  const { t } = useTranslation();
  const { colors, typography, spacing, radius, cardGradeColor } = useAppTheme();
  const [filter, setFilter] = useState<Filter>('ALL');
  const rows = players
    .filter(({ slot }) => {
      const group = playerGroup(slot);
      return group && (filter === 'ALL' || filter === group);
    })
    .sort((a, b) => b.score - a.score);

  return (
    <SectionCard title={t('deck_ranking_title')}>
      <View style={{ gap: spacing.md }}>
        <SegmentedTabs
          tabs={(Object.keys(FILTER_LABELS) as Filter[]).map((key) => ({ key, label: t(FILTER_LABELS[key]) }))}
          selected={filter}
          onSelect={setFilter}
        />
        {rows.length > 0 ? (
          <Text style={{ ...typography.caption, alignSelf: 'flex-end', color: colors.secondaryText }}>
            {t('deck_score_final')} · {t('deck_score_stat')} + {t('deck_score_skill')}
          </Text>
        ) : null}
        {rows.length === 0 ? (
          <Text style={{ ...typography.body, color: colors.secondaryText }}>{t('deck_ranking_empty')}</Text>
        ) : rows.map((player, index) => {
          const grade = cardTypeLabel(player.cardType);
          const accent = cardGradeColor(player.cardType).hex;
          const order = player.battingOrder != null
            ? `${player.battingOrder}${t('deck_batting_order_suffix')}`
            : player.relieverRole
              ? t(RELIEVER_ROLE_KEYS[player.relieverRole])
              : undefined;
          const skills = player.perSkill.map(({ name, appliedGrade }) =>
            appliedGrade ? `${name} ${appliedGrade}` : name,
          ).join(' · ');

          return (
            <Pressable
              key={player.slot}
              accessibilityRole="button"
              onPress={() => onPress(player.slot)}
              style={({ pressed }) => ({
                flexDirection: 'row',
                alignItems: 'center',
                gap: spacing.sm,
                backgroundColor: colors.surfaceVariant,
                borderRadius: radius.control,
                padding: spacing.sm,
                opacity: pressed ? 0.75 : 1,
              })}
            >
              <Text style={{ ...typography.label, color: colors.secondaryText, minWidth: 22, fontVariant: ['tabular-nums'] }}>
                {index + 1}
              </Text>
              <View style={{ alignSelf: 'stretch', width: 3, backgroundColor: accent, borderRadius: radius.pill }} />
              <View style={{ flex: 1, minWidth: 0, gap: spacing.xs }}>
                <Text style={{ ...typography.caption, color: colors.secondaryText }}>
                  {player.slot}{order ? ` · ${order}` : ''}
                </Text>
                <Text style={{ ...typography.card, color: colors.onSurface }}>
                  {player.playerName?.trim() || grade}
                </Text>
                {player.playerName?.trim() ? (
                  <Text style={{ ...typography.label, color: accent }}>{grade}</Text>
                ) : null}
                {skills ? (
                  <Text style={{ ...typography.caption, color: colors.secondaryText }}>{skills}</Text>
                ) : null}
              </View>
              <View style={{ alignItems: 'flex-end', gap: spacing.xs }}>
                <Text style={{ ...typography.card, color: colors.accentValue, fontVariant: ['tabular-nums'] }}>
                  {player.score.toFixed(2)}
                </Text>
                <Text style={{ ...typography.caption, color: colors.secondaryText, fontVariant: ['tabular-nums'] }}>
                  {player.statScore.toFixed(2)} + {player.skillScore.toFixed(2)}
                </Text>
              </View>
            </Pressable>
          );
        })}
      </View>
    </SectionCard>
  );
}
