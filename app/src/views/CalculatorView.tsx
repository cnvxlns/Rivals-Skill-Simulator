import React, { useState } from 'react';
import { Pressable, Text, View, ViewStyle } from 'react-native';
import { CARD_GRADES_LOW_TO_HIGH, CardVariant, Handedness, Position, SubPosition, variantsFor } from '../types';
import { useScoreCalculator } from '../lib/useScoreCalculator';
import { canPlaceSkill } from '../lib/cardRules';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';
import { cardTypeLabel } from '../lib/format';
import {
  EmptyState,
  InfoBanner,
  LabeledDropdown,
  LinkAction,
  NumberField,
  PrimaryActionButton,
  ScoreHero,
  SegmentedTabs,
  SectionCard,
  TooltipTarget,
} from '../components/ui';
import { columnsFor, useResponsive } from '../lib/useResponsive';

// 낮은 등급부터. 서열이 곧 순서다.
const cardGradeOptions = CARD_GRADES_LOW_TO_HIGH;

/**
 * 두 벌을 좌우로 나란히 두기 시작하는 폭.
 *
 * 계산기 하나가 이미 isSplit(1000)에서 폭을 다 쓴다. 그보다 넉넉해야 반으로 갈라도
 * 슬롯 카드와 점수가 눌리지 않는다.
 */
const COMPARE_SPLIT = 1360;

const TICKET_LABEL_KEYS = {
  SKILL_CHANGE: 'ticket_kind_normal',
  PREMIUM_SKILL_CHANGE: 'ticket_kind_premium',
  SUPREME_SKILL_CHANGE: 'ticket_kind_supreme',
} as const;

export default function CalculatorView({ onViewMethodology }: { onViewMethodology?: () => void }) {
  const calc = useScoreCalculator();
  const { t } = useTranslation();
  const { colors, typography, radius, spacing, tabularNums } = useAppTheme();
  const { width, isWide, isSplit } = useResponsive();

  const controlWidth = isSplit ? '18.5%' : isWide ? '31%' : '47.5%';
  const statWidth = isSplit ? '23.5%' : isWide ? '31%' : '47.5%';
  const controlGrid: ViewStyle = {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: isWide ? spacing.mdl : spacing.smd,
  };
  const controlField: ViewStyle = {
    flexBasis: controlWidth,
    maxWidth: controlWidth,
    flexGrow: 1,
    minWidth: 0,
  };
  const statField: ViewStyle = {
    flexBasis: statWidth,
    maxWidth: statWidth,
    flexGrow: 1,
    minWidth: 0,
  };
  // 슬롯은 카드 종류에 따라 3개 또는 4개다. 열 폭 하한을 두고 폭에서 열 수를 구한다.
  // 컨테이너가 gap을 쓰므로 정확히 100/n%로 두면 마지막 열이 다음 줄로 밀린다. 1%p 뺀다.
  // 사용자 스탯은 기본값으로 두는 경우가 대부분이라 접어둔 채로 시작한다.
  const [statsOpen, setStatsOpen] = useState(false);

  const slotColumns = columnsFor(width, 540, { min: 2, max: 4 });
  const slotBasis = `${100 / slotColumns - 1}%` as const;
  const slotCard: ViewStyle | undefined = isSplit
    ? { flexBasis: slotBasis, maxWidth: slotBasis, flexGrow: 1 }
    : undefined;
  const linkTouchTarget: ViewStyle = {
    minHeight: 44,
    alignSelf: 'flex-start',
    justifyContent: 'center',
  };
  const tk = (key: string) => t(key as never);

  const pitcherSubs: SubPosition[] = ['ALL', 'SP', 'RP', 'CP'];
  const batterSubs: SubPosition[] = ['ALL', 'C', '1B', '2B', '3B', 'SS', 'LF', 'CF', 'RF', 'DH'];
  const subOptions = calc.position === Position.PITCHER ? pitcherSubs : batterSubs;

  const opponentStats =
    calc.position === Position.PITCHER
      ? new Set(['파워', '정확', '선구', '인내', '주루'])
      : new Set(['구속', '변화', '구위', '제구', '지구력']);

  /**
   * 비교를 켜면 좌우로 나란히 둔다. 계산기 하나가 이미 1000px를 다 쓰므로 그보다
   * 넉넉할 때만 나눈다. 좁거나 네이티브면 A/B 전환으로 떨어진다.
   */
  const sideBySide = calc.compare && width >= COMPARE_SPLIT;
  const [shownSet, setShownSet] = useState<'a' | 'b'>('a');
  const visibleSets = sideBySide ? [0, 1] : [calc.compare && shownSet === 'b' ? 1 : 0];
  const setLabel = (index: number) => t(index === 0 ? 'calculator_set_a' : 'calculator_set_b');

  /** 한 벌의 스킬 슬롯 격자. 비교를 켜면 좌우로 두 번 그린다. */
  function renderSlots(setIndex: number) {
    return (
      <View
        style={
          isSplit && !sideBySide
            ? { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md }
            : { gap: spacing.md }
        }
      >
        {Array.from({ length: calc.slotCount }, (_, index) => index).map((index) => {
          const selection = calc.sets[setIndex].selections[index];
          const selectedSkill = calc.skills.find((skill) => skill.skillId === selection?.skillId);
          // 등급을 바꿔도 고른 스킬을 비우지 않으므로 새 카드의 풀 밖인 것이 남을 수 있다.
          const stale = calc.unavailableSlots[setIndex]?.[index] ?? false;
          const maxLevel = selectedSkill?.maxLevel ?? 1;
          const levelOptions = Array.from({ length: maxLevel }, (_, levelIndex) => levelIndex + 1);
          return (
            <View
              key={`slot-${index}`}
              style={[
                {
                  backgroundColor: colors.surface,
                  borderRadius: radius.card,
                  borderWidth: 1,
                  borderColor: colors.outlineFaint,
                  padding: spacing.mdl,
                  gap: spacing.sm,
                },
                // 좌우로 나눌 때 슬롯은 세로로 쌓인다. 이때 flexBasis는 폭이 아니라
                // 높이로 먹어서 카드가 눌리고 뒤 요소와 겹친다.
                isSplit && !sideBySide ? slotCard : undefined,
              ]}
            >
              <Text style={[typography.card, { color: colors.onSurface }]}>
                {t('slot_label')} {index + 1}
              </Text>
              <LabeledDropdown
                label={t('score_select_skill')}
                selected={selection?.skillId ?? ''}
                options={[
                  '',
                  // 못 쓰게 된 선택도 목록에 남긴다. 빼면 빈 칸으로 보여 스킬이 지워진
                  // 것처럼 읽힌다. 이름을 모르면 ID가 대신 나온다.
                  ...(stale && selection?.skillId ? [selection.skillId] : []),
                  ...calc.skills
                    // 이 칸에 나올 수 없는 스킬은 목록에서 뺀다(모먼트 전용은 첫 칸에만,
                    // 블랙은 카드당 한 장). 등장 확률표에서 0%인 조합이다.
                    .filter((skill) =>
                      canPlaceSkill(
                        calc.cardGrade,
                        calc.cardVariant,
                        index,
                        skill.skillId,
                        calc.sets[setIndex].selections
                          .filter((_, other) => other !== index)
                          .map((other) => other.skillId),
                      ),
                    )
                    .map((skill) => skill.skillId),
                ]}
                optionLabel={(skillId) =>
                  calc.skills.find((skill) => skill.skillId === skillId)?.name ??
                  (skillId ? skillId : t('score_select_skill'))
                }
                onSelect={(skillId) => calc.updateSkill(setIndex, index, skillId)}
                searchable
                searchPlaceholder={t('score_table_search_placeholder')}
              />
              {stale ? <InfoBanner text={t('skill_unavailable')} tone="error" /> : null}
              <LabeledDropdown
                label={t('score_level')}
                selected={Math.min(Math.max(selection?.level ?? 1, 1), maxLevel)}
                options={levelOptions}
                // 스킬이 없으면 레벨이 의미가 없다. 빈 라벨을 주면 placeholder가 대신 나온다.
                optionLabel={(level) =>
                  selectedSkill ? selectedSkill.levelLabels?.[level - 1] ?? `Lv ${level}` : ''
                }
                placeholder={t('score_level_empty')}
                disabled={!selectedSkill}
                onSelect={(level) => calc.updateLevel(setIndex, index, level)}
              />
              <LinkAction
                text={t('score_clear_slot')}
                onPress={() => calc.clearSlot(setIndex, index)}
                style={linkTouchTarget}
              />
            </View>
          );
        })}
      </View>
    );
  }

  return (
    <View style={{ gap: spacing.md }}>
      <View style={{ gap: spacing.xs }}>
        <Text style={[typography.title, { color: colors.onSurface }]}>{t('calculator_title')}</Text>
        <Text style={[typography.label, { color: colors.secondaryText }]}>{t('calculator_desc')}</Text>
      </View>

      <SectionCard title={t('score_settings')}>
        <View style={controlGrid}>
          <View style={controlField}>
            <LabeledDropdown
              label={t('label_card_type')}
              selected={calc.cardGrade}
              options={cardGradeOptions}
              optionLabel={(option) => cardTypeLabel(option)}
              onSelect={(option) => calc.setCardGrade(option)}
            />
          </View>
          <View style={controlField}>
            <LabeledDropdown
              label={t('label_position')}
              selected={calc.position}
              options={[Position.PITCHER, Position.BATTER]}
              optionLabel={(option) =>
                option === Position.PITCHER ? t('position_pitcher') : t('position_batter')
              }
              onSelect={(option) => calc.setPosition(option)}
            />
          </View>
          <View style={controlField}>
            <LabeledDropdown
              label={t('label_sub_position')}
              selected={(calc.subPosition || 'ALL') as SubPosition}
              options={subOptions}
              optionLabel={(option) => (option === 'ALL' ? t('option_all_sub_positions') : option)}
              onSelect={(option) => calc.setSubPosition(option === 'ALL' ? '' : option)}
            />
          </View>
          {calc.position === Position.PITCHER ? (
            <View style={controlField}>
              <LabeledDropdown
                label={t('label_throw_hand')}
                selected={calc.throwHand}
                options={[Handedness.RIGHT, Handedness.LEFT]}
                optionLabel={(option) =>
                  option === Handedness.LEFT ? t('hand_left_throw') : t('hand_right_throw')
                }
                onSelect={(option) => calc.setThrowHand(option)}
              />
            </View>
          ) : (
            <View style={controlField}>
              <LabeledDropdown
                label={t('label_bat_hand')}
                selected={calc.batHand}
                options={[Handedness.RIGHT, Handedness.LEFT, Handedness.SWITCH]}
                optionLabel={(option) =>
                  option === Handedness.LEFT
                    ? t('hand_left_bat')
                    : option === Handedness.SWITCH
                      ? t('hand_switch')
                      : t('hand_right_bat')
                }
                onSelect={(option) => calc.setBatHand(option)}
              />
            </View>
          )}
          {calc.position === Position.BATTER ? (
            <View style={controlField}>
              <LabeledDropdown
                label={t('label_batting_order')}
                selected={calc.battingOrder}
                options={[1, 2, 3, 4, 5, 6, 7, 8, 9]}
                optionLabel={(option) => String(option)}
                onSelect={(option) => calc.updateBattingOrder(option)}
              />
            </View>
          ) : null}
          {calc.position === Position.PITCHER &&
          (calc.subPosition === 'SP' || calc.subPosition === 'RP') ? (
            <View style={controlField}>
              <LabeledDropdown
                label={t('label_pitcher_slot')}
                selected={calc.pitcherSlot}
                options={calc.subPosition === 'SP' ? [null, 1, 2, 3, 4, 5] : [null, 1, 2, 3, 4, 5, 6]}
                optionLabel={(option) => (option == null ? '-' : String(option))}
                onSelect={(option) => calc.updatePitcherSlot(option)}
              />
            </View>
          ) : null}
        </View>
      </SectionCard>

      {/*
        스탯은 대부분 기본값 그대로 쓰고 스킬 조합만 바꿔 본다. 슬롯 위에 두되 접어서
        기본 흐름(설정 -> 슬롯 -> 계산)을 가리지 않게 한다.
      */}
      <SectionCard
        title={t('score_user_stats')}
        right={
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            {statsOpen ? (
              <LinkAction text={t('score_reset_stats')} onPress={calc.resetUserStats} style={linkTouchTarget} />
            ) : null}
            <LinkAction
              text={statsOpen ? t('action_collapse') : t('action_expand')}
              onPress={() => setStatsOpen((open) => !open)}
              style={linkTouchTarget}
            />
          </View>
        }
      >
        {statsOpen ? (
          <View style={controlGrid}>
            {calc.visibleStats.map((stat) => (
              <View key={stat} style={statField}>
                <NumberField
                  label={tk(`stat_${stat}`)}
                  value={String(Math.round(calc.userStats[stat] ?? 0))}
                  onChangeText={(raw) => calc.updateUserStat(stat, parseFloat(raw) || 0)}
                />
              </View>
            ))}
          </View>
        ) : null}
      </SectionCard>

      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md, flexWrap: 'wrap' }}>
        <Text style={[typography.card, { color: colors.onSurface, flex: 1 }]}>
          {t('score_skill_slots')}
        </Text>
        {calc.compare ? (
          <LinkAction text={t('calculator_copy_a_to_b')} onPress={calc.copyAToB} style={linkTouchTarget} />
        ) : null}
      </View>

      {/* 좌우로 못 나눌 때는 어느 벌을 보는지 고르게 한다. */}
      {calc.compare && !sideBySide ? (
        <SegmentedTabs
          tabs={[
            { key: 'a' as const, label: setLabel(0) },
            { key: 'b' as const, label: setLabel(1) },
          ]}
          selected={shownSet}
          onSelect={setShownSet}
          stretch
        />
      ) : null}

      <View style={sideBySide ? { flexDirection: 'row', gap: spacing.md } : undefined}>
        {visibleSets.map((setIndex) => (
          <View key={`slots-${setIndex}`} style={sideBySide ? { flex: 1, minWidth: 0, gap: spacing.sm } : { gap: spacing.sm }}>
            {/* 좌우로 나눌 때만 붙인다. 위아래로 볼 때는 A/B 전환 탭이 이미 알려 준다. */}
            {sideBySide ? (
              <Text style={[typography.label, { color: colors.secondaryText }]}>{setLabel(setIndex)}</Text>
            ) : null}
            {renderSlots(setIndex)}
          </View>
        ))}
      </View>

      {calc.error ? <InfoBanner text={tk(calc.error)} tone="error" /> : null}
      <PrimaryActionButton
        text={t('score_calculate')}
        onPress={calc.calculate}
        enabled={!calc.calculating}
        loading={calc.calculating}
      />
      {/*
        비교는 슬롯 제목 옆 링크였다. 두 벌을 만드는 큰 동작인데 링크로는 눈에 띄지 않아
        계산 버튼 바로 아래 같은 모양으로 내려놓는다.
      */}
      <PrimaryActionButton
        text={calc.compare ? t('calculator_compare_off') : t('calculator_compare_on')}
        onPress={() => calc.setCompare(!calc.compare)}
        enabled={!calc.calculating}
      />

      {calc.hasResult ? (
        <View style={sideBySide ? { flexDirection: 'row', gap: spacing.md } : { gap: spacing.md }}>
          {visibleSets.map((setIndex) => renderResult(setIndex))}
        </View>
      ) : !calc.calculating && !calc.error ? (
        <SectionCard padding={spacing.xxl}>
          <EmptyState text={t('calculator_empty')} />
        </SectionCard>
      ) : null}

      {renderTickets()}
    </View>
  );

  /**
   * 스킬 변경권 기댓값.
   *
   * 계산기가 이미 들고 있는 조건을 그대로 쓰므로 여기서 새로 물어보는 건 잠금 여부뿐이다.
   */
  function renderTickets() {
    const filled = calc.sets[0].selections.filter((selection) => selection.skillId).length;
    return (
      <SectionCard title={t('ticket_title')}>
        <View style={{ gap: spacing.md }}>
          <Text style={[typography.label, { color: colors.secondaryText }]}>{t('ticket_desc')}</Text>

          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md, flexWrap: 'wrap' }}>
            <LinkAction
              text={`${t('ticket_lock_slot_one')}${calc.lockSlotOne ? ' ✓' : ''}`}
              onPress={() => calc.setLockSlotOne(!calc.lockSlotOne)}
              style={linkTouchTarget}
            />
            {calc.tickets && !calc.tickets.slotOneLockable ? (
              <Text style={[typography.label, { color: colors.muted, flex: 1 }]}>
                {t('ticket_lock_unavailable')}
              </Text>
            ) : null}
          </View>

          {/*
            스킬레벨보호권은 켜고 끄게 두지 않는다. 끄고 보는 경우가 없어 선택지만 늘렸다.
            숫자가 그 가정 위에 서 있으므로 한 줄로 밝혀 둔다.
          */}
          <Text style={[typography.label, { color: colors.muted }]}>{t('ticket_protect_assumed')}</Text>

          <PrimaryActionButton
            text={t('ticket_calculate')}
            onPress={calc.evaluateTickets}
            enabled={filled > 0 && !calc.ticketsLoading}
            loading={calc.ticketsLoading}
          />

          {calc.tickets
            ? calc.tickets.tickets.map((outcome) => {
                const impossible = outcome.expectedTickets == null;
                return (
                  <View
                    key={outcome.ticket}
                    style={{
                      gap: spacing.xs,
                      paddingVertical: spacing.smd,
                      borderBottomWidth: 1,
                      borderBottomColor: colors.divider,
                    }}
                  >
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
                      <Text style={[typography.card, { color: colors.onSurface, flex: 1 }]}>
                        {t(TICKET_LABEL_KEYS[outcome.ticket])}
                      </Text>
                      <Text
                        style={[
                          typography.card,
                          { color: impossible ? colors.muted : colors.accentValue },
                          tabularNums,
                        ]}
                      >
                        {impossible
                          ? t('ticket_impossible')
                          : `${t('ticket_expected')} ${outcome.expectedTickets?.toFixed(1)}${t('ticket_expected_unit')}`}
                      </Text>
                    </View>

                    <View style={{ flexDirection: 'row', gap: spacing.md, flexWrap: 'wrap' }}>
                      <Text style={[typography.label, { color: colors.secondaryText }]}>
                        {t('ticket_chance_per_one')} {(outcome.improveChance * 100).toFixed(1)}%
                      </Text>
                      {outcome.averageGain != null ? (
                        <Text style={[typography.label, { color: colors.statMine }]}>
                          {t('ticket_avg_gain')} +{outcome.averageGain.toFixed(2)}
                        </Text>
                      ) : null}
                    </View>

                    {!impossible ? (
                      <View style={{ flexDirection: 'row', gap: spacing.md, flexWrap: 'wrap' }}>
                        {Object.entries(outcome.chanceWithin).map(([count, chance]) => (
                          <Text
                            key={count}
                            style={[typography.label, { color: colors.secondaryText }, tabularNums]}
                          >
                            {count}
                            {t('ticket_within_suffix')} {(chance * 100).toFixed(0)}%
                          </Text>
                        ))}
                      </View>
                    ) : null}
                  </View>
                );
              })
            : null}

        </View>
      </SectionCard>
    );
  }

  /** 한 벌의 결과. B에는 A와의 차이를 함께 보여 준다. */
  function renderResult(setIndex: number) {
    const result = calc.sets[setIndex].result;
    if (!result) return null;
    const base = calc.sets[0].result;
    const delta = setIndex > 0 && base ? result.total - base.total : null;
    const mineStatResults = result.perStat.filter((stat) => !opponentStats.has(stat.stat));
    const opponentStatResults = result.perStat.filter((stat) => opponentStats.has(stat.stat));
    return (
      <SectionCard
        key={`result-${setIndex}`}
        title={calc.compare ? `${t('score_result_ready')} · ${setLabel(setIndex)}` : t('score_result_ready')}
        style={sideBySide ? { flex: 1, minWidth: 0 } : undefined}
      >
        <ScoreHero label={t('score_total')} value={result.total} compact={sideBySide} />
        {delta != null ? (
          <Text
            style={[
              typography.card,
              { color: delta >= 0 ? colors.statMine : colors.statOpponent },
              tabularNums,
            ]}
          >
            {t('calculator_delta')} {delta >= 0 ? '+' : '−'}
            {Math.abs(delta).toFixed(2)}
          </Text>
        ) : null}

          {result.perSkill.length ? (
            <View style={{ gap: spacing.xs }}>
              <Text style={[typography.card, { color: colors.onSurface }]}>{t('score_by_skill')}</Text>
              <View>
                {result.perSkill.map((skill) => (
                  <View
                    key={skill.skillId}
                    style={{
                      minHeight: 44,
                      flexDirection: 'row',
                      alignItems: 'center',
                      gap: spacing.md,
                      paddingVertical: spacing.sm,
                      borderBottomWidth: 1,
                      borderBottomColor: colors.divider,
                    }}
                  >
                    {/*
                      결과 응답의 설명은 그 슬롯에서 고른 레벨로 x·y·z가 치환돼 있다.
                      구버전 응답이면 스킬 목록의 원문으로 떨어진다.
                    */}
                    <TooltipTarget
                      text={
                        skill.resolvedDescription ??
                        calc.skills.find((option) => option.skillId === skill.skillId)?.description
                      }
                      style={{ flex: 1, minWidth: 0 }}
                    >
                      <Text style={[typography.body, { color: colors.onSurface }]} numberOfLines={1}>
                        {skill.name}
                      </Text>
                    </TooltipTarget>
                    <Text
                      style={[
                        {
                          minWidth: 116,
                          color: colors.accentValue,
                          fontSize: 19,
                          fontWeight: '800',
                          textAlign: 'right',
                        },
                        tabularNums,
                      ]}
                    >
                      {skill.score.toFixed(2)}
                    </Text>
                  </View>
                ))}
              </View>
            </View>
          ) : null}

          {result.perStat.length ? (
            <View style={{ gap: spacing.md }}>
              <Text style={[typography.card, { color: colors.onSurface }]}>{t('score_by_stat')}</Text>

              {mineStatResults.length ? (
                <View>
                  <Text style={[typography.label, { color: colors.secondaryText, marginBottom: spacing.xs }]}>
                    {t('score_stat_mine')}
                  </Text>
                  {mineStatResults.map((stat) => (
                    <View
                      key={stat.stat}
                      style={{
                        minHeight: 44,
                        flexDirection: 'row',
                        alignItems: 'center',
                        gap: spacing.md,
                        paddingVertical: spacing.sm,
                        borderBottomWidth: 1,
                        borderBottomColor: colors.divider,
                      }}
                    >
                      <Text style={[typography.body, { color: colors.onSurface, flex: 1 }]}>
                        {tk(`stat_${stat.stat}`)}
                      </Text>
                      <Text
                        style={[
                          {
                            minWidth: 116,
                            color: colors.statMine,
                            fontSize: 19,
                            fontWeight: '800',
                            textAlign: 'right',
                          },
                          tabularNums,
                        ]}
                      >
                        +{Math.abs(stat.value).toFixed(2)}
                      </Text>
                    </View>
                  ))}
                </View>
              ) : null}

              {opponentStatResults.length ? (
                <View>
                  <Text style={[typography.label, { color: colors.secondaryText, marginBottom: spacing.xs }]}>
                    {t('score_stat_opponent')}
                  </Text>
                  {opponentStatResults.map((stat) => (
                    <View
                      key={stat.stat}
                      style={{
                        minHeight: 44,
                        flexDirection: 'row',
                        alignItems: 'center',
                        gap: spacing.md,
                        paddingVertical: spacing.sm,
                        borderBottomWidth: 1,
                        borderBottomColor: colors.divider,
                      }}
                    >
                      <Text style={[typography.body, { color: colors.onSurface, flex: 1 }]}>
                        {tk(`stat_${stat.stat}`)}
                      </Text>
                      <Text
                        style={[
                          {
                            minWidth: 116,
                            color: colors.statOpponent,
                            fontSize: 19,
                            fontWeight: '800',
                            textAlign: 'right',
                          },
                          tabularNums,
                        ]}
                      >
                        −{Math.abs(stat.value).toFixed(2)}
                      </Text>
                    </View>
                  ))}
                </View>
              ) : null}
            </View>
          ) : null}

          {onViewMethodology ? (
            <LinkAction
              text={`${t('tab_methodology')} →`}
              onPress={onViewMethodology}
              style={{ ...linkTouchTarget, marginTop: spacing.sm }}
            />
          ) : null}
      </SectionCard>
    );
  }
}
