'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiErrorMessage, fetchPositionTraining, savePositionTraining } from './api';
import { PositionTraining, SkillLevelBonus, SlotTraining } from '../types';

/** 자리 하나가 가질 수 있는 스킬 레벨 보너스 수. 게임이 레벨 6·12·20에서 하나씩 준다. */
export const TRAINING_BONUS_SLOTS = 3;

/** 포훈으로 오르는 능력치. 게임의 '포지션 능력치' 탭에 보이는 것과 같은 묶음이다. */
export const TRAINING_BATTER_STATS = ['파워', '정확', '선구', '인내', '주루', '수비'];
export const TRAINING_PITCHER_STATS = ['구속', '변화', '구위', '제구', '지구력', '수비'];

const emptyBonuses = (): SkillLevelBonus[] =>
  Array.from({ length: TRAINING_BONUS_SLOTS }, () => ({ skillId: '', bonus: 1 }));

/**
 * 구단의 포지션 훈련 현황.
 *
 * 훈련은 선수가 아니라 자리에 붙고 모든 라인업에 공통이라 계정당 한 벌이다. 그래서 덱과
 * 따로 읽고 쓴다.
 *
 * @param enabled 로그인했는가. 로그인 전에 부르면 401이 돌아 전역 로그아웃이 돈다.
 */
export function usePositionTraining(enabled: boolean) {
  const [training, setTraining] = useState<PositionTraining>({ slots: {} });
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!enabled) {
      setTraining({ slots: {} });
      return;
    }
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      try {
        const loaded = await fetchPositionTraining();
        if (!cancelled) setTraining({ slots: loaded.slots ?? {} });
      } catch (err) {
        if (!cancelled) setError(apiErrorMessage(err));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  const slotOf = useCallback(
    (slot: string): SlotTraining => training.slots[slot] ?? {},
    [training],
  );

  /** 자리의 보너스 세 칸. 저장된 것이 모자라면 빈 칸으로 채운다. */
  const bonusesOf = useCallback(
    (slot: string): SkillLevelBonus[] => {
      const stored = slotOf(slot).skills ?? [];
      return emptyBonuses().map((empty, index) => stored[index] ?? empty);
    },
    [slotOf],
  );

  const patchSlot = useCallback((slot: string, patch: (current: SlotTraining) => SlotTraining) => {
    setSaved(false);
    setTraining((prev) => ({
      slots: { ...prev.slots, [slot]: patch(prev.slots[slot] ?? {}) },
    }));
  }, []);

  /** 값을 비우면 그 스탯을 지운다. 0과 "적지 않음"을 같게 둔다. */
  const updateStat = useCallback(
    (slot: string, stat: string, value: number | null) => {
      patchSlot(slot, (current) => {
        const stats = { ...(current.stats ?? {}) };
        if (value == null || Number.isNaN(value) || value === 0) delete stats[stat];
        else stats[stat] = value;
        return { ...current, stats };
      });
    },
    [patchSlot],
  );

  /**
   * 보너스 한 칸을 고친다. 같은 스킬이 한 자리에 두 번 나오지 않으므로 겹치면 먼저 것을 비운다.
   */
  const updateBonus = useCallback(
    (slot: string, index: number, patch: Partial<SkillLevelBonus>) => {
      patchSlot(slot, (current) => {
        const next = emptyBonuses().map((empty, idx) => (current.skills ?? [])[idx] ?? empty);
        next[index] = { ...next[index], ...patch };
        if (patch.skillId) {
          next.forEach((entry, idx) => {
            if (idx !== index && entry.skillId === patch.skillId) next[idx] = { skillId: '', bonus: 1 };
          });
        }
        return { ...current, skills: next.filter((entry) => entry.skillId) };
      });
    },
    [patchSlot],
  );

  const clearBonus = useCallback(
    (slot: string, index: number) => {
      patchSlot(slot, (current) => {
        const next = emptyBonuses().map((empty, idx) => (current.skills ?? [])[idx] ?? empty);
        next[index] = { skillId: '', bonus: 1 };
        return { ...current, skills: next.filter((entry) => entry.skillId) };
      });
    },
    [patchSlot],
  );

  /** 서버로 보낼 값. 아무것도 적지 않은 자리는 빼서 저장을 가볍게 둔다. */
  const payload = useMemo<PositionTraining>(() => {
    const slots: Record<string, SlotTraining> = {};
    Object.entries(training.slots).forEach(([slot, value]) => {
      const stats = value.stats ?? {};
      const skills = (value.skills ?? []).filter((entry) => entry.skillId);
      if (Object.keys(stats).length || skills.length) slots[slot] = { stats, skills };
    });
    return { slots };
  }, [training]);

  const save = useCallback(async () => {
    setSaving(true);
    setError(null);
    try {
      const stored = await savePositionTraining(payload);
      setTraining({ slots: stored.slots ?? {} });
      setSaved(true);
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }, [payload]);

  return {
    training: payload,
    slotOf,
    bonusesOf,
    updateStat,
    updateBonus,
    clearBonus,
    loading,
    saving,
    saved,
    error,
    save,
  };
}
