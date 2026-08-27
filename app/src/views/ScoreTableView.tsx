import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Text, TextInput, View } from 'react-native';
import { Handedness, Position, ScoreTableEntry, ScoreTableResponse, SubPosition } from '../types';
import { fetchScoreTable } from '../lib/api';
import { useScoreContext } from '../lib/useScoreContext';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';
import { InfoChip, LabeledDropdown, SectionCard } from '../components/ui';
import { useResponsive } from '../lib/useResponsive';

const TOP_N = 10;

/**
 * 점수표에 노출할 티어. 아이언·브론즈·실버는 실전에서 쓰지 않아 제외한다.
 * 검색에는 계속 걸리므로 개별 스킬 점수는 확인할 수 있다.
 */
const HIDDEN_TIERS = new Set(['iron', 'bronze', 'silver']);

export default function ScoreTableView() {
  const ctx = useScoreContext();
  const { t } = useTranslation();
  const { colors, typography, radius, spacing } = useAppTheme();
  const tk = (key: string) => t(key as never);

  const [table, setTable] = useState<ScoreTableResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');

  const { isWide, isSplit } = useResponsive();

  /** 넓은 화면에서 폼 컨트롤을 2열로 깐다. 드롭다운 하나가 전폭을 먹는 낭비를 없앤다. */
  const field = isWide ? { flexGrow: 1, flexBasis: 260, maxWidth: '49%' as const } : undefined;
  const formGrid = isWide
    ? { flexDirection: 'row' as const, flexWrap: 'wrap' as const, gap: 14, alignItems: 'flex-end' as const }
    : undefined;

  const pitcherSubs: SubPosition[] = ['ALL', 'SP', 'RP', 'CP'];
  const batterSubs: SubPosition[] = ['ALL', 'C', '1B', '2B', '3B', 'SS', 'LF', 'CF', 'RF', 'DH'];
  const subOptions = ctx.position === Position.PITCHER ? pitcherSubs : batterSubs;

  // 세부 포지션이 없으면 백엔드에 범용 포지션을 넘긴다. 이때 포지션 게이트 스킬은 0점이 된다.
  const requestPosition = ctx.hasSpecificPosition ? ctx.subPosition : ctx.position;

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchScoreTable(
        {
          position: requestPosition,
          battingOrder: ctx.position === Position.BATTER ? ctx.battingOrder : undefined,
          pitcherSlot: ctx.position === Position.PITCHER ? ctx.pitcherSlot : undefined,
          throwHand: ctx.position === Position.PITCHER ? ctx.throwHand : undefined,
          batHand: ctx.position === Position.BATTER ? ctx.batHand : undefined,
          userStats: ctx.userStats,
        },
        0, // 전체를 받아 검색까지 클라이언트에서 처리한다 (233개라 부담 없음)
      );
      setTable(data);
    } catch {
      setError('score_table_error');
    } finally {
      setLoading(false);
    }
  }, [requestPosition, ctx.position, ctx.battingOrder, ctx.pitcherSlot, ctx.throwHand, ctx.batHand, ctx.userStats]);

  useEffect(() => {
    load();
  }, [load]);

  const searchResults = useMemo(() => {
    const q = query.trim();
    if (!q || !table) return [];
    const hits: { tier: string; entry: ScoreTableEntry }[] = [];
    for (const group of table.tiers) {
      for (const entry of group.entries) {
        if (entry.name.includes(q) || entry.skillId.toUpperCase().includes(q.toUpperCase())) {
          hits.push({ tier: group.tier, entry });
        }
      }
    }
    return hits.slice(0, 20);
  }, [query, table]);

  const Row = ({ rank, entry }: { rank: number | null; entry: ScoreTableEntry }) => {
    const inactive = entry.score === 0;
    return (
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          gap: spacing.sm,
          paddingVertical: 7,
          borderBottomWidth: 1,
          borderColor: colors.outline,
          opacity: inactive ? 0.55 : 1,
        }}
      >
        {rank != null ? (
          <Text style={[typography.labelMedium, { color: colors.muted, width: 22, textAlign: 'right' }]}>{rank}</Text>
        ) : null}
        <View style={{ flex: 1 }}>
          <Text style={[typography.bodyMedium, { color: colors.onSurface }]} numberOfLines={1}>
            {entry.name}
          </Text>
          {inactive ? (
            <Text style={[typography.bodySmall, { color: colors.muted }]}>{t('score_table_inactive')}</Text>
          ) : null}
        </View>
        <InfoChip text={entry.appliedGrade} />
        <Text
          style={[
            typography.titleSmall,
            { color: inactive ? colors.muted : colors.primary, width: 62, textAlign: 'right' },
          ]}
        >
          {entry.score.toFixed(2)}
        </Text>
      </View>
    );
  };

  return (
    <View style={{ gap: 12 }}>
      <Text style={[typography.headlineSmall, { color: colors.onBackground }]}>{t('score_table_title')}</Text>
      <Text style={[typography.bodyMedium, { color: colors.secondaryText }]}>{t('score_table_desc')}</Text>

      <SectionCard title={t('score_settings')}>
        <View style={formGrid}>
        <View style={field}><LabeledDropdown
          label={t('label_position')}
          selected={ctx.position}
          options={[Position.PITCHER, Position.BATTER]}
          optionLabel={(o) => (o === Position.PITCHER ? t('position_pitcher') : t('position_batter'))}
          onSelect={(o) => ctx.setPosition(o)}
        /></View>
        <View style={field}>
        <LabeledDropdown
          label={t('label_sub_position')}
          selected={(ctx.subPosition || 'ALL') as SubPosition}
          options={subOptions}
          optionLabel={(o) => (o === 'ALL' ? t('option_all_sub_positions') : o)}
          onSelect={(o) => ctx.setSubPosition(o === 'ALL' ? '' : o)}
        /></View>
        {ctx.position === Position.PITCHER ? (
          <View style={field}><LabeledDropdown
            label={t('label_throw_hand')}
            selected={ctx.throwHand}
            options={[Handedness.RIGHT, Handedness.LEFT]}
            optionLabel={(o) => (o === Handedness.LEFT ? t('hand_left_throw') : t('hand_right_throw'))}
            onSelect={(o) => ctx.setThrowHand(o)}
          /></View>
        ) : (
          <View style={field}>
          <LabeledDropdown
            label={t('label_bat_hand')}
            selected={ctx.batHand}
            options={[Handedness.RIGHT, Handedness.LEFT, Handedness.SWITCH]}
            optionLabel={(o) =>
              o === Handedness.LEFT ? t('hand_left_bat') : o === Handedness.SWITCH ? t('hand_switch') : t('hand_right_bat')
            }
            onSelect={(o) => ctx.setBatHand(o)}
          /></View>
        )}
        {ctx.position === Position.BATTER ? (
          <View style={field}><LabeledDropdown
            label={t('label_batting_order')}
            selected={ctx.battingOrder}
            options={[null, 1, 2, 3, 4, 5, 6, 7, 8, 9]}
            optionLabel={(o) => (o == null ? t('option_average_batting_order') : String(o))}
            onSelect={(o) => ctx.setBattingOrder(o)}
          /></View>
        ) : null}
        {ctx.position === Position.PITCHER && (ctx.subPosition === 'SP' || ctx.subPosition === 'RP') ? (
          <View style={field}><LabeledDropdown
            label={t('label_pitcher_slot')}
            selected={ctx.pitcherSlot}
            options={ctx.subPosition === 'SP' ? [null, 1, 2, 3, 4, 5] : [null, 1, 2, 3, 4, 5, 6]}
            optionLabel={(o) => (o == null ? '-' : String(o))}
            onSelect={(o) => ctx.setPitcherSlot(o)}
          /></View>
        ) : null}

        </View>

        {!ctx.hasSpecificPosition ? (
          <View
            style={{
              backgroundColor: colors.surfaceVariant,
              borderColor: colors.outline,
              borderWidth: 1,
              borderRadius: radius.small,
              padding: 10,
            }}
          >
            <Text style={[typography.bodySmall, { color: colors.secondaryText }]}>
              {t('score_table_pick_sub_position')}
            </Text>
          </View>
        ) : null}
      </SectionCard>

      <SectionCard title={t('score_table_search')}>
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder={t('score_table_search_placeholder')}
          placeholderTextColor={colors.muted}
          style={{
            backgroundColor: colors.surfaceVariant,
            borderColor: colors.outline,
            borderWidth: 1,
            borderRadius: radius.small,
            paddingHorizontal: 14,
            paddingVertical: 10,
            color: colors.onSurface,
          }}
        />
        {query.trim() ? (
          searchResults.length ? (
            searchResults.map(({ tier, entry }) => (
              <View key={entry.skillId} style={{ gap: 2 }}>
                <Text style={[typography.labelMedium, { color: colors.secondaryText }]}>{tk(`tier_${tier}`)}</Text>
                <Row rank={null} entry={entry} />
              </View>
            ))
          ) : (
            <Text style={[typography.bodySmall, { color: colors.muted }]}>{t('score_table_no_match')}</Text>
          )
        ) : null}
      </SectionCard>

      {loading ? (
        <View style={{ paddingVertical: 40, alignItems: 'center', gap: 12 }}>
          <ActivityIndicator size="large" color={colors.primary} />
        </View>
      ) : null}

      {error ? <Text style={{ color: colors.error }}>{tk(error)}</Text> : null}

      {!loading && table ? (
        <View
          style={
            isSplit
              ? { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md, alignItems: 'flex-start' }
              : { gap: spacing.md }
          }
        >
          {table.tiers
            .filter((group) => !HIDDEN_TIERS.has(group.tier))
            .map((group) => (
              <SectionCard
                key={group.tier}
                title={`${tk(`tier_${group.tier}`)}  ·  ${group.totalCount}`}
                // 정확히 이등분한다. flexBasis만 두면 넓은 화면에서 3단이 된다.
                style={isSplit ? { width: '49%', flexGrow: 0, flexShrink: 0 } : undefined}
              >
                {group.entries.slice(0, TOP_N).map((entry, i) => (
                  <Row key={entry.skillId} rank={i + 1} entry={entry} />
                ))}
                {group.totalCount > TOP_N ? (
                  <Text style={[typography.bodySmall, { color: colors.muted, textAlign: 'right' }]}>
                    {t('score_table_more_prefix')}
                    {group.totalCount - TOP_N}
                    {t('score_table_more_suffix')}
                  </Text>
                ) : null}
              </SectionCard>
            ))}
        </View>
      ) : null}
    </View>
  );
}
