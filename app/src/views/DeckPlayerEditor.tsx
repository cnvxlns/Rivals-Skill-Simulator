'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Text, View } from 'react-native';
import {
  InfoBanner,
  LabeledDropdown,
  LinkAction,
  LoadingState,
  NumberField,
  SectionCard,
  TextField,
} from '../components/ui';
import { fetchScoreSkills } from '../lib/api';
import { cardTypeLabel } from '../lib/format';
import { useTranslation } from '../lib/i18n';
import { canPlaceSkill, slotCountFor } from '../lib/cardRules';
import { isBench, isLineup, isPitcher, isReliever, positionForSlot } from '../lib/useDeckEditor';
import { TRAINING_BATTER_STATS, TRAINING_PITCHER_STATS } from '../lib/usePositionTraining';
import { useAppTheme } from '../theme/useTheme';
import {
  CARD_GRADES_LOW_TO_HIGH,
  DeckRules,
  CardGrade,
  CardVariant,
  DeckPlayer,
  DeckSkillSelection,
  RelieverRole,
  ScoreSkillOption,
  variantsFor,
} from '../types';

const BATTING_ORDERS = Array.from({ length: 9 }, (_, i) => i + 1);

const BENCH_POSITIONS = ['C', '1B', '2B', '3B', 'SS', 'LF', 'CF', 'RF', 'DH'];

/**
 * 선수의 보유 능력치로 받는 스탯.
 *
 * 계산기와 같은 묶음이다. 덱 스코어(스페셜덱·팀덱)는 선수의 값이 아니라 덱 전체의
 * 지표라 여기서 받지 않고 서버 기본값을 쓴다.
 */
const PLAYER_STATS = { batter: TRAINING_BATTER_STATS, pitcher: TRAINING_PITCHER_STATS };

const RELIEVER_ROLE_KEYS = {
  [RelieverRole.WIN]: 'deck_reliever_win',
  [RelieverRole.CHASE]: 'deck_reliever_chase',
  [RelieverRole.LONG]: 'deck_reliever_long',
} as const;

/**
 * 자리 하나의 선수를 편집한다.
 *
 * 스킬 목록은 카드 타입과 포지션에 따라 달라지므로 둘 중 하나가 바뀌면 다시 불러온다.
 * 포지션은 후보만 직접 고르고 주전·투수는 자리에서 나온다.
 */
export default function DeckPlayerEditor({
  slot,
  player,
  slots: deckSlots,
  rules,
  onChange,
  onChangeBattingOrder,
}: {
  slot: string;
  player: DeckPlayer;
  /** 덱의 자리 전체. 능력치를 적은 자리를 고르는 데 쓴다. */
  slots: string[];
  /** 카드별 성장 상한. 없는 레벨을 고르지 못하게 한다. */
  rules: DeckRules | null;
  onChange: (patch: Partial<DeckPlayer>) => void;
  /** 타순은 다른 자리까지 밀어야 해서 별도로 받는다. 주전에만 쓰인다. */
  onChangeBattingOrder: (order: number) => void;
}) {
  const { t } = useTranslation();
  const { colors, spacing, typography } = useAppTheme();
  const [skills, setSkills] = useState<ScoreSkillOption[]>([]);
  const [loading, setLoading] = useState(false);

  const position = positionForSlot(slot, player.position);
  const slotCount = slotCountFor(player.cardGrade);
  const variant = (player.cardVariant as CardVariant) ?? CardVariant.NONE;
  const availableVariants = variantsFor(player.cardGrade as CardGrade);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      try {
        const loaded = await fetchScoreSkills(player.cardGrade, variant, position);
        if (!cancelled) setSkills(loaded);
      } catch {
        if (!cancelled) setSkills([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [player.cardGrade, variant, position]);

  const byId = useMemo(() => new Map(skills.map((s) => [s.skillId, s])), [skills]);

  /**
   * 이 카드의 이 칸에 놓을 수 있는 스킬인가.
   *
   * 스킬 ID 앞자리가 곧 풀이라 목록을 받아 오기 전에도, 목록에 없는 스킬이어도 판단할 수
   * 있다. 서버 응답에 기대면 요청이 실패했을 때 멀쩡한 선택까지 틀렸다고 말하게 된다.
   */
  const placeable = (skillId: string, index: number, ignore?: string) =>
    canPlaceSkill(
      String(player.cardGrade),
      variant,
      index,
      skillId,
      player.skills.map((s) => s.skillId).filter((id) => id !== ignore && id !== skillId),
    );

  // 슬롯 수는 카드 타입을 따라간다. 4칸에서 3칸으로 줄면 넘치는 것을 잘라 낸다.
  const slots = useMemo<(DeckSkillSelection | null)[]>(
    () => Array.from({ length: slotCount }, (_, i) => player.skills[i] ?? null),
    [slotCount, player.skills],
  );

  /**
   * 칸에 담긴 스킬을 저장한다.
   *
   * 칸 수를 넘는 스킬은 **버리지 않고 뒤에 그대로 붙인다.** 워크북에서 가져온 덱은 3칸
   * 카드에 스킬이 넷일 수 있는데(배포된 샘플이 그렇다), 첫 편집에서 조용히 잘리면 무엇이
   * 사라졌는지 아무도 모른다. 아래 "칸을 넘은 스킬"에서 눈으로 보고 지우게 한다.
   */
  const commit = useCallback(
    (next: (DeckSkillSelection | null)[]) => {
      const kept = next.filter((s): s is DeckSkillSelection => !!s);
      onChange({ skills: [...kept, ...player.skills.slice(slotCount)] });
    },
    [onChange, player.skills, slotCount],
  );

  /** 칸 수를 넘겨 들어온 스킬. 워크북은 게임 규칙을 검사하지 않는다. */
  const overflowSkills = player.skills.slice(slotCount);

  const removeOverflow = (index: number) => {
    const next = [...player.skills];
    next.splice(slotCount + index, 1);
    onChange({ skills: next });
  };

  const setSkillAt = (index: number, skillId: string) => {
    const next = [...slots];
    if (!skillId) {
      next[index] = null;
    } else {
      const skill = byId.get(skillId);
      // 처음 넣을 때는 S 등급을 기본으로 둔다. 점수표와 같은 기준이라 비교하기 쉽다.
      const sIndex = skill?.levelLabels?.indexOf('S') ?? -1;
      const level = sIndex >= 0 ? sIndex + 1 : Math.max(1, skill?.maxLevel ?? 1);
      next[index] = { skillId, level };
    }
    commit(next);
  };

  const setLevelAt = (index: number, level: number) => {
    const next = [...slots];
    const current = next[index];
    if (!current) return;
    next[index] = { ...current, level };
    commit(next);
  };

  // 같은 선수가 같은 스킬을 두 번 가질 수 없다. 이미 고른 것은 목록에서 뺀다.
  const chosen = new Set(slots.filter(Boolean).map((s) => s!.skillId));

  const statNames = isPitcher(slot) ? PLAYER_STATS.pitcher : PLAYER_STATS.batter;
  const playerStats = player.stats ?? {};

  /** 비우면 그 스탯을 지운다. 값이 없으면 서버 기본값으로 채점된다. */
  const setStat = (stat: string, raw: string) => {
    const next = { ...playerStats };
    const value = parseFloat(raw);
    if (raw.trim() === '' || Number.isNaN(value)) delete next[stat];
    else next[stat] = value;
    onChange({ stats: next });
  };

  /**
   * 능력치를 적을 수 있는 자리. 투수 값을 타자 자리에서 적었다고 할 수는 없다.
   *
   * 빈 값은 "지금 자리"라는 뜻이고, 그때는 포훈 보정이 걸리지 않는다.
   */
  const statsSlotOptions = ['', ...deckSlots.filter((each) => isPitcher(each) === isPitcher(slot))];

  /** 능력치 점수에 들어가는 스탯. 성분을 쌓는 칸은 이것만 받는다. */
  const growthStats = isPitcher(slot) ? ['변화', '구위'] : ['파워', '정확', '선구'];

  type GrowthField = 'baseStats' | 'trainingStats' | 'specialTrainingStats';

  /** 비우면 그 스탯을 지운다. 0과 "적지 않음"을 같게 둔다. */
  const setGrowthStat = (field: GrowthField, stat: string, raw: string) => {
    const next = { ...(player[field] ?? {}) };
    const value = parseFloat(raw);
    if (raw.trim() === '' || Number.isNaN(value)) delete next[stat];
    else next[stat] = value;
    onChange({ [field]: next });
  };

  /**
   * 이 카드가 고를 수 있는 레벨.
   *
   * 카드마다 상한이 다르다 — 시그니처의 초월은 9, 블랙의 강화는 10에서 끝난다. 표에 아예
   * 없는 등급(라이브·시즌·임팩트)은 목록이 비어 드롭다운이 잠긴다.
   */
  const levelsFor = (track: 'TRANSCENDENCE' | 'ENHANCEMENT'): number[] => {
    const limit = rules?.growth.find(
      (entry) =>
        entry.track === track &&
        entry.cardGrade === String(player.cardGrade) &&
        entry.cardVariant === String(player.cardVariant ?? CardVariant.NONE),
    ) ?? rules?.growth.find(
      (entry) =>
        entry.track === track &&
        entry.cardGrade === String(player.cardGrade) &&
        entry.cardVariant === 'NONE',
    );
    if (!limit) return [];
    const first = track === 'TRANSCENDENCE' ? 0 : 1;
    return Array.from({ length: limit.maxLevel - first + 1 }, (_, i) => first + i);
  };

  const transcendenceLevels = levelsFor('TRANSCENDENCE');
  const enhancementLevels = levelsFor('ENHANCEMENT');

  return (
    <View style={{ gap: spacing.lg }}>
      <SectionCard title={`${slot} · ${t('deck_player_title')}`}>
        <View style={{ gap: spacing.lg }}>
          <TextField
            label={t('deck_label_player_name')}
            value={player.playerName ?? ''}
            onChangeText={(v) => onChange({ playerName: v })}
            placeholder={t('deck_placeholder_player_name')}
          />

          <LabeledDropdown
            label={t('label_card_grade')}
            selected={player.cardGrade as CardGrade}
            options={CARD_GRADES_LOW_TO_HIGH}
            optionLabel={(value) => cardTypeLabel(value)}
            onSelect={(value) => {
              // 등급을 잘못 골랐다가 되돌릴 때 고른 스킬까지 사라지는 것이 가장 잦은 불편이라
              // 스킬은 그대로 둔다. 새 카드에 없는 스킬은 슬롯마다 경고로 알리고, 저장은
              // 백엔드가 막는다. 다만 칸이 줄면(블랙 4 → 3) 넘치는 것은 버릴 수밖에 없다.
              // 새 등급에 없는 변형이면 기본형으로 되돌린다.
              const next = variantsFor(value).includes(variant) ? variant : CardVariant.NONE;
              onChange({
                cardGrade: value,
                cardVariant: next,
                skills: player.skills.slice(0, slotCountFor(value)),
              });
            }}
          />

          {availableVariants.length > 1 ? (
            <LabeledDropdown
              label={t('label_card_variant')}
              selected={variant}
              options={availableVariants}
              optionLabel={(v) => (v === CardVariant.NONE ? t('option_variant_none') : v)}
              onSelect={(value) => onChange({ cardVariant: value })}
            />
          ) : null}

          {isLineup(slot) ? (
            <LabeledDropdown
              label={t('label_batting_order')}
              selected={player.battingOrder ?? 1}
              options={BATTING_ORDERS}
              // 고른 번호로 끼워 넣고 나머지가 밀린다. 예전에는 그 번호를 쓰던 자리를
              // 접미사로 보여 줬는데, 맞바꾸기가 아니게 되면서 그 표기가 거짓말이 됐다.
              optionLabel={(order) => `${order}${t('deck_batting_order_suffix')}`}
              onSelect={onChangeBattingOrder}
            />
          ) : null}

          {isBench(slot) ? (
            <LabeledDropdown
              label={t('label_position')}
              selected={player.position ?? 'C'}
              options={BENCH_POSITIONS}
              optionLabel={(v) => v}
              onSelect={(value) => onChange({ position: value, skills: [] })}
            />
          ) : null}

          {isReliever(slot) ? (
            <LabeledDropdown
              label={t('deck_label_reliever_role')}
              selected={player.relieverRole ?? RelieverRole.LONG}
              options={Object.values(RelieverRole)}
              optionLabel={(v) => t(RELIEVER_ROLE_KEYS[v])}
              onSelect={(value) => onChange({ relieverRole: value })}
            />
          ) : null}
        </View>
      </SectionCard>

      <SectionCard title={t('score_user_stats')}>
        <View style={{ gap: spacing.lg }}>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md }}>
            {statNames.map((stat) => (
              <View key={stat} style={{ flexGrow: 1, flexBasis: 120, maxWidth: 200 }}>
                <NumberField
                  label={stat}
                  value={playerStats[stat] != null ? String(playerStats[stat]) : ''}
                  onChangeText={(raw) => setStat(stat, raw)}
                />
              </View>
            ))}
          </View>

          {/*
            보유 능력치에는 적을 당시 자리의 포훈이 이미 들어 있다. 그래서 값을 다시 더하지
            않고, 다른 자리에 세웠을 때만 두 자리의 차이를 보정한다.
          */}
          <LabeledDropdown
            label={t('deck_stats_slot')}
            selected={player.statsSlot ?? ''}
            options={statsSlotOptions}
            optionLabel={(value) => value || t('deck_stats_slot_current')}
            onSelect={(value) => onChange({ statsSlot: value || null })}
          />
          <Text style={{ ...typography.label, color: colors.muted }}>{t('deck_stats_slot_hint')}</Text>
        </View>
      </SectionCard>

      {/*
        성분으로 쌓는 길. 기본 능력치를 적은 스탯만 여기 값을 더해 최종 능력치를 만든다.
        비워 두면 위의 보유 능력치를 그대로 쓴다. 스탯 단위로 갈리므로 파워만 성분으로,
        나머지는 보유 값으로 두는 것도 된다.
      */}
      <SectionCard title={t('deck_base_stats')}>
        <View style={{ gap: spacing.lg }}>
          <Text style={{ ...typography.label, color: colors.muted }}>
            {t('deck_base_stats_hint')}
          </Text>
          {(
            [
              ['baseStats', t('deck_base_stats')],
              ['trainingStats', t('deck_stats_training')],
              ['specialTrainingStats', t('deck_stats_special')],
            ] as const
          ).map(([field, label]) => (
            <View key={field} style={{ gap: spacing.sm }}>
              <Text style={{ ...typography.label, color: colors.secondaryText }}>{label}</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md }}>
                {growthStats.map((stat) => (
                  <View key={stat} style={{ flexGrow: 1, flexBasis: 120, maxWidth: 200 }}>
                    <NumberField
                      label={stat}
                      value={player[field]?.[stat] != null ? String(player[field]![stat]) : ''}
                      onChangeText={(raw) => setGrowthStat(field, stat, raw)}
                    />
                  </View>
                ))}
              </View>
            </View>
          ))}

          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md }}>
            <View style={{ flexGrow: 1, flexBasis: 120, maxWidth: 200 }}>
              <NumberField
                label={t('deck_player_year')}
                value={player.year != null ? String(player.year) : ''}
                onChangeText={(raw) => {
                  const value = parseInt(raw, 10);
                  onChange({ year: Number.isNaN(value) ? null : value });
                }}
              />
            </View>
            <View style={{ flexGrow: 1, flexBasis: 140, maxWidth: 220 }}>
              <LabeledDropdown
                label={t('deck_player_transcendence')}
                selected={player.transcendenceLevel ?? -1}
                options={[-1, ...transcendenceLevels]}
                optionLabel={(level) => (level < 0 ? '—' : String(level))}
                onSelect={(level) => onChange({ transcendenceLevel: level < 0 ? null : level })}
                disabled={transcendenceLevels.length === 0}
              />
            </View>
            <View style={{ flexGrow: 1, flexBasis: 140, maxWidth: 220 }}>
              <LabeledDropdown
                label={t('deck_player_enhancement')}
                selected={player.enhancementLevel ?? -1}
                options={[-1, ...enhancementLevels]}
                optionLabel={(level) => (level < 0 ? '—' : String(level))}
                onSelect={(level) => onChange({ enhancementLevel: level < 0 ? null : level })}
                disabled={enhancementLevels.length === 0}
              />
            </View>
          </View>
          {transcendenceLevels.length === 0 || enhancementLevels.length === 0 ? (
            <Text style={{ ...typography.label, color: colors.muted }}>
              {t('deck_player_growth_none')}
            </Text>
          ) : null}
        </View>
      </SectionCard>

      <SectionCard title={t('score_select_skills')}>
        {loading ? (
          <LoadingState />
        ) : (
          <View style={{ gap: spacing.lg }}>
            {slots.map((selection, index) => {
              const skill = selection ? byId.get(selection.skillId) : undefined;
              const stale = !!selection && !placeable(selection.skillId, index);
              const maxLevel = Math.max(1, skill?.maxLevel ?? 1);
              const options = [
                '',
                // 지금 카드에 못 쓰는 선택도 목록에 넣는다. 빼면 드롭다운이 빈 칸으로 보여
                // 스킬이 지워진 것처럼 읽힌다. 이름을 모르면 ID가 대신 나온다.
                ...(stale && selection ? [selection.skillId] : []),
                ...skills
                  .filter((s) => s.skillId === selection?.skillId || !chosen.has(s.skillId))
                  // 이 칸에 나올 수 없는 스킬은 아예 고르지 못하게 한다.
                  // (모먼트 전용은 첫 칸에만, 블랙은 카드당 한 장.)
                  .filter((s) => placeable(s.skillId, index, selection?.skillId))
                  .map((s) => s.skillId),
              ];
              return (
                <View key={index} style={{ gap: spacing.sm }}>
                  <LabeledDropdown
                    label={`${t('slot_label')} ${index + 1}`}
                    selected={selection?.skillId ?? ''}
                    options={options}
                    // 이름을 모르는 것은 이 카드 밖의 스킬뿐이다. 그때는 ID를 그대로 보여 준다.
                    optionLabel={(skillId) =>
                      byId.get(skillId)?.name ?? (skillId ? skillId : t('score_select_skill'))
                    }
                    onSelect={(skillId) => setSkillAt(index, skillId)}
                    searchable
                    searchPlaceholder={t('score_table_search_placeholder')}
                  />
                  {stale ? <InfoBanner text={t('skill_unavailable')} tone="error" /> : null}
                  <LabeledDropdown
                    label={t('score_level')}
                    selected={Math.min(Math.max(selection?.level ?? 1, 1), maxLevel)}
                    options={Array.from({ length: maxLevel }, (_, i) => i + 1)}
                    // 스킬이 없으면 레벨이 의미가 없다. 빈 라벨을 주면 placeholder가 대신 나온다.
                    optionLabel={(level) => (skill ? skill.levelLabels?.[level - 1] ?? `Lv ${level}` : '')}
                    placeholder={t('score_level_empty')}
                    disabled={!skill}
                    onSelect={(level) => setLevelAt(index, level)}
                  />
                </View>
              );
            })}

            {/*
              칸을 넘겨 들어온 스킬. 워크북에서 가져온 덱에만 생긴다 — 워크북은 게임 규칙을
              검사하지 않아서 3칸 카드에 스킬이 넷 적혀 있을 수 있다. 조용히 자르지 않고
              여기 세워 두면 무엇을 버리는지 보고 지울 수 있다.
            */}
            {overflowSkills.length > 0 ? (
              <View style={{ gap: spacing.sm }}>
                <InfoBanner text={t('deck_skill_problem_overflow')} tone="error" />
                {overflowSkills.map((selection, index) => (
                  <View
                    key={`${selection.skillId}-${index}`}
                    style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}
                  >
                    <Text style={{ ...typography.body, color: colors.onSurface, flex: 1 }}>
                      {byId.get(selection.skillId)?.name ?? selection.skillId}
                    </Text>
                    <LinkAction text={t('score_clear_slot')} onPress={() => removeOverflow(index)} />
                  </View>
                ))}
              </View>
            ) : null}
          </View>
        )}
      </SectionCard>
    </View>
  );
}
