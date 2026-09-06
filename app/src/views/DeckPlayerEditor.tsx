'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { View } from 'react-native';
import { LabeledDropdown, LoadingState, SectionCard, TextField } from '../components/ui';
import { fetchScoreSkills } from '../lib/api';
import { cardTypeLabel } from '../lib/format';
import { useTranslation } from '../lib/i18n';
import { isBench, isReliever, positionForSlot, slotCountFor } from '../lib/useDeckEditor';
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
}: {
  slot: string;
  player: DeckPlayer;
  onChange: (patch: Partial<DeckPlayer>) => void;
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
              // 등급이 바뀌면 스킬 풀과 슬롯 수가 달라지므로 고른 스킬을 비운다.
              // 새 등급에 없는 변형이면 기본형으로 되돌린다.
              const next = variantsFor(value).includes(variant) ? variant : CardVariant.NONE;
              onChange({ cardGrade: value, cardVariant: next, skills: [] });
            }}
          />

          {availableVariants.length > 1 ? (
            <LabeledDropdown
              label={t('label_card_variant')}
              selected={variant}
              options={availableVariants}
              optionLabel={(v) => (v === CardVariant.NONE ? t('option_variant_none') : v)}
              onSelect={(value) => onChange({ cardVariant: value, skills: [] })}
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
              const maxLevel = Math.max(1, skill?.maxLevel ?? 1);
              const options = [
                '',
                ...skills
                  .filter((s) => s.skillId === selection?.skillId || !chosen.has(s.skillId))
                  .map((s) => s.skillId),
              ];
              return (
                <View key={index} style={{ gap: spacing.sm }}>
                  <LabeledDropdown
                    label={`${t('slot_label')} ${index + 1}`}
                    selected={selection?.skillId ?? ''}
                    options={options}
                    optionLabel={(skillId) => byId.get(skillId)?.name ?? t('score_select_skill')}
                    onSelect={(skillId) => setSkillAt(index, skillId)}
                    searchable
                    searchPlaceholder={t('score_table_search_placeholder')}
                  />
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
