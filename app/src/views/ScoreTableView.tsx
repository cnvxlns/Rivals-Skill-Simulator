import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Text, View } from 'react-native';
import { Handedness, Position, ScoreTableEntry, ScoreTableResponse, SubPosition } from '../types';
import { fetchScoreTable } from '../lib/api';
import { useScoreContext } from '../lib/useScoreContext';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';
import {
  ErrorState,
  GradeChip,
  InfoBanner,
  LabeledDropdown,
  LinkAction,
  SearchInput,
  SectionCard,
  Skeleton,
  TierChip,
  TooltipTarget,
} from '../components/ui';
import { columnsFor, useResponsive } from '../lib/useResponsive';

const TOP_N = 10;

/**
 * 점수표에 노출할 티어와 그 순서. 아이언·브론즈·실버는 실전에서 쓰지 않아 제외한다.
 * 검색에는 계속 걸리므로 개별 스킬 점수는 확인할 수 있다.
 */
const VISIBLE_TIERS = ['gold', 'hof', 'moment', 'wbc', 'black'];

export default function ScoreTableView() {
  const ctx = useScoreContext();
  const { t } = useTranslation();
  const { colors, typography, spacing, radius, tabularNums, tierColor, inactiveRow } = useAppTheme();
  const tk = (key: string) => t(key as never);

  const [table, setTable] = useState<ScoreTableResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  // 세로로 긴 화면에서 조건 카드가 결과를 밀어내지 않게 접을 수 있다.
  const [conditionsOpen, setConditionsOpen] = useState(true);
  // 티어별 펼침 여부. 데이터는 이미 전량 받아두므로 자르기만 풀면 된다.
  const [expandedTiers, setExpandedTiers] = useState<Record<string, boolean>>({});

  const { width, isWide, isSplit } = useResponsive();

  /**
   * 조건 컨트롤 열 수. 1000px+ 5열 / 720px+ 3열 / 그 이하 2열.
   *
   * 컨트롤은 4개뿐이라 폭이 늘어도 열을 늘리지 않는다. 늘리면 드롭다운만 좁아진다.
   */
  const columns = isSplit ? 5 : isWide ? 3 : 2;

  /** 티어 카드 열 수. 카드 하나가 440px 아래로 좁아지지 않는 선에서 최대한 늘린다. */
  const tierColumns = columnsFor(width, 500, { min: 2, max: 5 });
  const gridGap = isWide ? 14 : 10;
  const cell = {
    // gap을 뺀 나머지를 균등 분할한다. flexBasis만 두면 열 수가 화면 폭에 따라 흔들린다.
    width: `${100 / columns}%` as const,
    paddingRight: gridGap,
    paddingBottom: gridGap,
  };
  const grid = { flexDirection: 'row' as const, flexWrap: 'wrap' as const, marginRight: -gridGap, marginBottom: -gridGap };

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
      // 조건이 바뀌면 순위가 통째로 달라진다. 펼쳐둔 상태를 유지할 이유가 없다.
      setExpandedTiers({});
    } catch {
      setError('score_table_error');
    } finally {
      setLoading(false);
    }
  }, [requestPosition, ctx.position, ctx.battingOrder, ctx.pitcherSlot, ctx.throwHand, ctx.batHand, ctx.userStats]);

  useEffect(() => {
    load();
  }, [load]);

  /** 화면에 그릴 티어 그룹. 디자인이 정한 순서(골드→HOF→모먼트→WBC→블랙)로 고정한다. */
  const groups = useMemo(() => {
    if (!table) return [];
    const byTier = new Map(table.tiers.map((g) => [String(g.tier).toLowerCase(), g]));
    return VISIBLE_TIERS.map((tier) => byTier.get(tier)).filter((g): g is NonNullable<typeof g> => Boolean(g));
  }, [table]);

  const totalCount = useMemo(() => groups.reduce((sum, g) => sum + g.totalCount, 0), [groups]);

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

  /**
   * 순위 행. 미발동(0점)이면 순위·이름·점수·등급칩 네 곳을 동시에 죽인다.
   * 색 하나에만 의존하지 않도록 사유 문구도 항상 붙인다.
   */
  const Row = ({ rank, tier, entry }: { rank: number | null; tier?: string; entry: ScoreTableEntry }) => {
    const inactive = entry.score === 0;
    return (
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          gap: isWide ? 15 : 12,
          paddingVertical: 14,
          borderBottomWidth: 1,
          borderColor: colors.divider,
        }}
      >
        {rank != null ? (
          <Text
            style={[
              {
                width: isWide ? 30 : 24,
                textAlign: 'right',
                fontSize: isWide ? 15 : 14.5,
                fontWeight: '700',
                color: inactive ? inactiveRow.rank : colors.muted,
              },
              tabularNums,
            ]}
          >
            {rank}
          </Text>
        ) : null}
        {tier ? <TierChip tier={tier} label={tk(`tier_${tier}`)} /> : null}

        <View style={{ flex: 1, gap: 4, minWidth: 0 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            {/* 표는 등급 하나로 고정 채점하므로 x·y·z를 실제 수치로 바꾼 설명을 쓴다. */}
            <TooltipTarget text={entry.resolvedDescription ?? entry.description} style={{ flexShrink: 1, minWidth: 0 }}>
              <Text
                style={{
                  fontSize: isWide ? 16 : 15.5,
                  fontWeight: '600',
                  color: inactive ? inactiveRow.name : colors.onSurface,
                }}
                numberOfLines={1}
              >
                {entry.name}
              </Text>
            </TooltipTarget>
            {/* 모바일은 열을 3개로 줄이려고 등급 칩을 스킬명 옆으로 올린다. */}
            {!isWide ? <GradeChip grade={entry.appliedGrade} inactive={inactive} compact /> : null}
          </View>
          {inactive ? (
            <Text style={{ fontSize: isWide ? 13 : 12.5, color: colors.mutedFaint }} numberOfLines={1}>
              {t('score_table_inactive')}
            </Text>
          ) : null}
        </View>

        {isWide ? <GradeChip grade={entry.appliedGrade} inactive={inactive} /> : null}

        <Text
          style={[
            {
              width: isWide ? 100 : undefined,
              textAlign: 'right',
              fontSize: isWide ? 19 : 18,
              fontWeight: '800',
              color: inactive ? inactiveRow.score : colors.accentValue,
            },
            tabularNums,
          ]}
        >
          {entry.score.toFixed(2)}
        </Text>
      </View>
    );
  };

  return (
    <View style={{ gap: spacing.md }}>
      <Text style={[typography.title, { color: colors.onSurface }]}>{t('score_table_title')}</Text>
      <Text style={{ color: colors.secondaryText, fontSize: 16, lineHeight: 24 }}>{t('score_table_desc')}</Text>

      {/* ── 조건 설정 ── */}
      <SectionCard
        title={t('score_settings')}
        padding={isWide ? spacing.xxl : spacing.lg}
        right={
          isWide ? (
            <Text style={{ color: colors.muted, fontSize: 14 }} numberOfLines={1}>
              {t('score_table_condition_hint')}
            </Text>
          ) : (
            <LinkAction
              text={conditionsOpen ? t('action_collapse') : t('action_expand')}
              onPress={() => setConditionsOpen((v) => !v)}
            />
          )
        }
      >
        {conditionsOpen ? (
          <View style={grid}>
            <View style={cell}>
              <LabeledDropdown
                label={t('label_position')}
                selected={ctx.position}
                options={[Position.PITCHER, Position.BATTER]}
                optionLabel={(o) => (o === Position.PITCHER ? t('position_pitcher') : t('position_batter'))}
                onSelect={(o) => ctx.setPosition(o)}
              />
            </View>
            <View style={cell}>
              <LabeledDropdown
                label={t('label_sub_position')}
                selected={(ctx.subPosition || 'ALL') as SubPosition}
                options={subOptions}
                optionLabel={(o) => (o === 'ALL' ? t('option_all_sub_positions') : o)}
                onSelect={(o) => ctx.setSubPosition(o === 'ALL' ? '' : o)}
              />
            </View>
            <View style={cell}>
              {ctx.position === Position.PITCHER ? (
                <LabeledDropdown
                  label={t('label_throw_hand')}
                  selected={ctx.throwHand}
                  options={[Handedness.RIGHT, Handedness.LEFT]}
                  optionLabel={(o) => (o === Handedness.LEFT ? t('hand_left_throw') : t('hand_right_throw'))}
                  onSelect={(o) => ctx.setThrowHand(o)}
                />
              ) : (
                <LabeledDropdown
                  label={t('label_bat_hand')}
                  selected={ctx.batHand}
                  options={[Handedness.RIGHT, Handedness.LEFT, Handedness.SWITCH]}
                  optionLabel={(o) =>
                    o === Handedness.LEFT
                      ? t('hand_left_bat')
                      : o === Handedness.SWITCH
                        ? t('hand_switch')
                        : t('hand_right_bat')
                  }
                  onSelect={(o) => ctx.setBatHand(o)}
                />
              )}
            </View>
            {ctx.position === Position.BATTER ? (
              <View style={cell}>
                <LabeledDropdown
                  label={t('label_batting_order')}
                  selected={ctx.battingOrder}
                  options={[null, 1, 2, 3, 4, 5, 6, 7, 8, 9]}
                  optionLabel={(o) => (o == null ? t('option_average_batting_order') : String(o))}
                  onSelect={(o) => ctx.setBattingOrder(o)}
                />
              </View>
            ) : null}
            {ctx.position === Position.PITCHER && (ctx.subPosition === 'SP' || ctx.subPosition === 'RP') ? (
              <View style={cell}>
                <LabeledDropdown
                  label={t('label_pitcher_slot')}
                  selected={ctx.pitcherSlot}
                  options={ctx.subPosition === 'SP' ? [null, 1, 2, 3, 4, 5] : [null, 1, 2, 3, 4, 5, 6]}
                  optionLabel={(o) => (o == null ? '-' : String(o))}
                  onSelect={(o) => ctx.setPitcherSlot(o)}
                />
              </View>
            ) : null}
          </View>
        ) : null}

        {!ctx.hasSpecificPosition ? <InfoBanner text={t('score_table_banner')} /> : null}
      </SectionCard>

      {/* ── 검색 ── */}
      <View style={{ flexDirection: isWide ? 'row' : 'column', alignItems: isWide ? 'center' : 'stretch', gap: spacing.md }}>
        <View style={{ flex: isWide ? 1 : undefined }}>
          <SearchInput
            value={query}
            onChangeText={setQuery}
            placeholder={t('score_table_search_placeholder')}
            height={isWide ? 52 : 48}
          />
        </View>
        {table ? (
          <Text style={{ color: colors.muted, fontSize: 14.5 }} numberOfLines={1}>
            {t('score_table_count_prefix')}
            <Text style={[{ color: colors.onSurface }, tabularNums]}>{totalCount}</Text>
            {t('score_table_count_middle')}
            <Text style={[{ color: colors.onSurface }, tabularNums]}>{groups.length}</Text>
            {t('score_table_count_suffix')}
          </Text>
        ) : null}
      </View>

      {/* 검색 결과는 티어 카드 위에 삽입된다. 순위 번호 대신 티어 칩을 붙인다. */}
      {query.trim() ? (
        <SectionCard title={t('score_table_search')} padding={isWide ? spacing.xxl : spacing.lg}>
          {searchResults.length ? (
            searchResults.map(({ tier, entry }) => <Row key={entry.skillId} rank={null} tier={tier} entry={entry} />)
          ) : (
            <Text style={{ color: colors.muted, fontSize: 15 }}>{t('score_table_no_match')}</Text>
          )}
        </SectionCard>
      ) : null}

      {loading ? <Skeleton rows={6} /> : null}

      {error && !loading ? <ErrorState message={tk(error)} onRetry={load} retryText={t('btn_retry')} /> : null}

      {/* ── 티어 그룹 ── */}
      {!loading && !error && table ? (
        <View
          style={
            isSplit
              ? { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'flex-start', marginRight: -22, marginBottom: -22 }
              : { gap: spacing.md }
          }
        >
          {groups.map((group) => {
            const rail = tierColor(group.tier).hex;
            return (
              <View
                key={group.tier}
                // 폭에서 계산한 열 수로 정확히 나눈다. flexBasis만 두면 열 수가 흔들린다.
                style={isSplit ? { width: `${100 / tierColumns}%` as const, paddingRight: 22, paddingBottom: 22 } : undefined}
              >
                <View
                  style={{
                    backgroundColor: colors.surface,
                    borderWidth: 1,
                    borderColor: colors.outlineFaint,
                    borderRadius: isWide ? radius.cardLg : radius.card,
                    overflow: 'hidden',
                  }}
                >
                  {/* 티어색은 이 레일과 아래 칩 두 곳에서만 쓴다. 행 안으로 번지게 하지 않는다. */}
                  <View style={{ height: 4, backgroundColor: rail }} />

                  <View
                    style={{
                      flexDirection: 'row',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: spacing.md,
                      paddingHorizontal: isWide ? 26 : 20,
                      paddingTop: 22,
                      paddingBottom: 8,
                    }}
                  >
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14, flexShrink: 1 }}>
                      <TierChip tier={group.tier} label={tk(`tier_${group.tier}`)} />
                      <Text style={{ color: colors.muted, fontSize: 14 }} numberOfLines={1}>
                        {t('score_table_group_total_prefix')}
                        {group.totalCount}
                        {t('score_table_group_total_suffix')}
                      </Text>
                    </View>
                    <Text style={{ color: colors.muted, fontSize: 13, fontWeight: '600', letterSpacing: 0.66 }}>
                      {t('score_table_top_n')}
                    </Text>
                  </View>

                  <View style={{ paddingHorizontal: isWide ? 26 : 20, paddingTop: 10, paddingBottom: 24 }}>
                    {(expandedTiers[group.tier] ? group.entries : group.entries.slice(0, TOP_N)).map(
                      (entry, i) => (
                        <Row key={entry.skillId} rank={i + 1} entry={entry} />
                      ),
                    )}
                    {group.totalCount > TOP_N ? (
                      <View style={{ paddingTop: 16, alignItems: 'flex-end' }}>
                        <LinkAction
                          text={
                            expandedTiers[group.tier]
                              ? t('action_collapse')
                              : `${t('score_table_more_view_prefix')}${group.totalCount - TOP_N}${t('score_table_more_view_suffix')}`
                          }
                          onPress={() =>
                            setExpandedTiers((prev) => ({ ...prev, [group.tier]: !prev[group.tier] }))
                          }
                        />
                      </View>
                    ) : null}
                  </View>
                </View>
              </View>
            );
          })}
        </View>
      ) : null}
    </View>
  );
}
