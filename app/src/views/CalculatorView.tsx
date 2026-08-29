import React from 'react';
import { Text, View, ViewStyle } from 'react-native';
import { CardType, Handedness, Position, SubPosition } from '../types';
import { useScoreCalculator } from '../lib/useScoreCalculator';
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
  SectionCard,
  TooltipTarget,
} from '../components/ui';
import { columnsFor, useResponsive } from '../lib/useResponsive';

const cardTypeOptions = Object.values(CardType);

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
  const slotColumns = columnsFor(width, 480, { min: 2, max: 4 });
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

  const mineStatResults = calc.result?.perStat.filter((stat) => !opponentStats.has(stat.stat)) ?? [];
  const opponentStatResults = calc.result?.perStat.filter((stat) => opponentStats.has(stat.stat)) ?? [];

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
              selected={calc.cardType}
              options={cardTypeOptions}
              optionLabel={(option) => cardTypeLabel(option)}
              onSelect={(option) => calc.setCardType(option)}
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
                options={[null, 1, 2, 3, 4, 5, 6, 7, 8, 9]}
                optionLabel={(option) =>
                  option == null ? t('option_average_batting_order') : String(option)
                }
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

      <Text style={[typography.card, { color: colors.onSurface }]}>{t('score_skill_slots')}</Text>

      <View
        style={
          isSplit
            ? { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md }
            : { gap: spacing.md }
        }
      >
        {Array.from({ length: calc.slotCount }, (_, index) => index).map((index) => {
          const selection = calc.selections[index];
          const selectedSkill = calc.skills.find((skill) => skill.skillId === selection?.skillId);
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
                slotCard,
              ]}
            >
              <Text style={[typography.card, { color: colors.onSurface }]}>
                {t('slot_label')} {index + 1}
              </Text>
              <LabeledDropdown
                label={t('score_select_skill')}
                selected={selection?.skillId ?? ''}
                options={['', ...calc.skills.map((skill) => skill.skillId)]}
                optionLabel={(skillId) =>
                  calc.skills.find((skill) => skill.skillId === skillId)?.name ?? t('score_select_skill')
                }
                onSelect={(skillId) => calc.updateSkill(index, skillId)}
                searchable
                searchPlaceholder={t('score_table_search_placeholder')}
              />
              <LabeledDropdown
                label={t('score_level')}
                selected={Math.min(Math.max(selection?.level ?? 1, 1), maxLevel)}
                options={levelOptions}
                optionLabel={(level) => selectedSkill?.levelLabels?.[level - 1] ?? `Lv ${level}`}
                onSelect={(level) => calc.updateLevel(index, level)}
              />
              <LinkAction
                text={t('score_clear_slot')}
                onPress={() => calc.clearSlot(index)}
                style={linkTouchTarget}
              />
            </View>
          );
        })}
      </View>

      <SectionCard
        title={t('score_user_stats')}
        right={<LinkAction text={t('score_reset_stats')} onPress={calc.resetUserStats} style={linkTouchTarget} />}
      >
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
      </SectionCard>

      {calc.error ? <InfoBanner text={tk(calc.error)} tone="error" /> : null}
      <PrimaryActionButton
        text={t('score_calculate')}
        onPress={calc.calculate}
        enabled={!calc.calculating}
        loading={calc.calculating}
      />

      {calc.result ? (
        <SectionCard title={t('score_result_ready')}>
          <ScoreHero label={t('score_total')} value={calc.result.total} />

          {calc.result.perSkill.length ? (
            <View style={{ gap: spacing.xs }}>
              <Text style={[typography.card, { color: colors.onSurface }]}>{t('score_by_skill')}</Text>
              <View>
                {calc.result.perSkill.map((skill) => (
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
                          minWidth: 100,
                          color: colors.accentValue,
                          fontSize: 17,
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

          {calc.result.perStat.length ? (
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
                            minWidth: 100,
                            color: colors.statMine,
                            fontSize: 17,
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
                            minWidth: 100,
                            color: colors.statOpponent,
                            fontSize: 17,
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
      ) : !calc.calculating && !calc.error ? (
        <SectionCard padding={spacing.xxl}>
          <EmptyState text={t('calculator_empty')} />
        </SectionCard>
      ) : null}
    </View>
  );
}
