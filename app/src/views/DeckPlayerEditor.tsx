'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { View } from 'react-native';
import { InfoBanner, LabeledDropdown, LoadingState, SectionCard, TextField } from '../components/ui';
import { fetchScoreSkills } from '../lib/api';
import { cardTypeLabel } from '../lib/format';
import { useTranslation } from '../lib/i18n';
import { canPlaceSkill, slotCountFor } from '../lib/cardRules';
import { isBench, isLineup, isReliever, positionForSlot } from '../lib/useDeckEditor';
import { useAppTheme } from '../theme/useTheme';
import {
  CARD_GRADES_LOW_TO_HIGH,
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
  onChange,
  onChangeBattingOrder,
}: {
  slot: string;
  player: DeckPlayer;
  onChange: (patch: Partial<DeckPlayer>) => void;
  /** 타순은 다른 자리까지 밀어야 해서 별도로 받는다. 주전에만 쓰인다. */
  onChangeBattingOrder: (order: number) => void;
}) {
  const { t } = useTranslation();
  const { spacing } = useAppTheme();
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

  const commit = useCallback(
    (next: (DeckSkillSelection | null)[]) => {
      onChange({ skills: next.filter((s): s is DeckSkillSelection => !!s) });
    },
    [onChange],
  );

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
          </View>
        )}
      </SectionCard>
    </View>
  );
}
