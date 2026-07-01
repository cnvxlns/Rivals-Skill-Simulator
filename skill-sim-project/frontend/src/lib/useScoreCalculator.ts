'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { calculateScore, fetchScoreSkills } from './api';
import {
  CardType,
  Position,
  ScoreRequest,
  ScoreResponse,
  ScoreSelection,
  ScoreSkillOption,
  SubPosition,
} from '../types';

const BASE_SLOT_COUNT = 3;
const DEFAULT_USER_STAT = 120;
const DEFAULT_DECK_SCORE = 500;
const BATTER_STATS = ['파워', '정확', '선구', '인내', '주루', '수비'];
const PITCHER_STATS = ['구속', '변화', '구위', '제구', '지구력', '수비'];
const DECK_STATS = ['스페셜덱', '팀덱'];

const slotCountForCard = (cardType: CardType) =>
  cardType === CardType.SIGNATURE_BLACK || cardType === CardType.WBC_SIGNATURE_BLACK ? 4 : BASE_SLOT_COUNT;

const defaultUserStats = () =>
  [...BATTER_STATS, ...PITCHER_STATS, ...DECK_STATS].reduce<Record<string, number>>((acc, stat) => {
    acc[stat] = DECK_STATS.includes(stat) ? DEFAULT_DECK_SCORE : DEFAULT_USER_STAT;
    return acc;
  }, {});

const defaultValueForStat = (stat: string) => (DECK_STATS.includes(stat) ? DEFAULT_DECK_SCORE : DEFAULT_USER_STAT);

export type ScoreSlotSelection = {
  skillId: string;
  level: number;
};

export function useScoreCalculator() {
  const [cardType, setCardType] = useState<CardType>(CardType.SIGNATURE);
  const [position, setPosition] = useState<Position>(Position.BATTER);
  const [subPosition, setSubPosition] = useState<SubPosition>('ALL');
  const [skills, setSkills] = useState<ScoreSkillOption[]>([]);
  const [selections, setSelections] = useState<ScoreSlotSelection[]>(Array(BASE_SLOT_COUNT).fill(null).map(() => ({ skillId: '', level: 1 })));
  const [result, setResult] = useState<ScoreResponse | null>(null);
  const [loadingSkills, setLoadingSkills] = useState(false);
  const [calculating, setCalculating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [userStats, setUserStats] = useState<Record<string, number>>(defaultUserStats);
  const [battingOrder, setBattingOrder] = useState<number | null>(null);

  const slotCount = useMemo(() => slotCountForCard(cardType), [cardType]);
  const scorePosition = subPosition === 'ALL' ? position : subPosition;
  const visibleStats = useMemo(
    () => [...(position === Position.PITCHER ? PITCHER_STATS : BATTER_STATS), ...DECK_STATS],
    [position],
  );

  const selectedSkillIds = useMemo(
    () => selections.map((selection) => selection.skillId).filter(Boolean),
    [selections],
  );

  const canCalculate = selections.length === slotCount && selections.every((selection) => selection.skillId);

  useEffect(() => {
    setSelections((prev) =>
      Array.from({ length: slotCount }, (_, idx) => prev[idx] ?? { skillId: '', level: 1 }),
    );
    setResult(null);
  }, [slotCount]);

  useEffect(() => {
    setSubPosition('ALL');
    setBattingOrder(null);
  }, [position]);

  useEffect(() => {
    let cancelled = false;

    const loadSkills = async () => {
      try {
        setLoadingSkills(true);
        setError(null);
        const loadedSkills = await fetchScoreSkills(cardType, scorePosition);
        if (cancelled) return;
        setSkills(loadedSkills);
        setSelections(Array.from({ length: slotCount }, () => ({ skillId: '', level: 1 })));
        setResult(null);
      } catch {
        if (cancelled) return;
        setSkills([]);
        setResult(null);
        setError('score_error_load');
      } finally {
        if (!cancelled) {
          setLoadingSkills(false);
        }
      }
    };

    loadSkills();

    return () => {
      cancelled = true;
    };
  }, [cardType, scorePosition, slotCount]);

  const updateSkill = useCallback((slotIndex: number, skillId: string) => {
    setSelections((prev) =>
      prev.map((selection, idx) => {
        if (idx !== slotIndex) return selection;
        const nextSkill = skills.find((skill) => skill.skillId === skillId);
        return {
          skillId,
          level: nextSkill ? Math.min(selection.level, nextSkill.maxLevel) || 1 : 1,
        };
      }),
    );
    setResult(null);
  }, [skills]);

  const updateLevel = useCallback((slotIndex: number, level: number) => {
    setSelections((prev) =>
      prev.map((selection, idx) => (idx === slotIndex ? { ...selection, level } : selection)),
    );
    setResult(null);
  }, []);

  const clearSlot = useCallback((slotIndex: number) => {
    setSelections((prev) =>
      prev.map((selection, idx) => (idx === slotIndex ? { skillId: '', level: 1 } : selection)),
    );
    setResult(null);
  }, []);

  const calculateWithStats = useCallback(async (stats: Record<string, number>) => {
    if (!canCalculate) {
      setError('score_fill_slots');
      return;
    }

    const payload: ScoreRequest = {
      cardType,
      position: scorePosition,
      selections: selections.map<ScoreSelection>((selection) => ({
        skillId: selection.skillId,
        level: selection.level,
      })),
      battingOrder: position === Position.BATTER ? battingOrder : undefined,
      userStats: stats,
    };

    try {
      setCalculating(true);
      setError(null);
      setResult(await calculateScore(payload));
    } catch {
      setResult(null);
      setError('score_error_calculate');
    } finally {
      setCalculating(false);
    }
  }, [battingOrder, canCalculate, cardType, position, scorePosition, selections]);

  const calculate = useCallback(async () => {
    await calculateWithStats(userStats);
  }, [calculateWithStats, userStats]);

  const updateUserStat = useCallback((stat: string, value: number) => {
    const nextValue = Number.isFinite(value) ? Math.max(0, value) : defaultValueForStat(stat);
    const nextStats = { ...userStats, [stat]: nextValue };
    setUserStats(nextStats);
    if (result && canCalculate) {
      void calculateWithStats(nextStats);
    } else {
      setResult(null);
    }
  }, [calculateWithStats, canCalculate, result, userStats]);

  const updateBattingOrder = useCallback((value: number | null) => {
    const nextValue = value != null && value >= 1 && value <= 9 ? value : null;
    setBattingOrder(nextValue);
    setResult(null);
  }, []);

  const resetUserStats = useCallback(() => {
    const nextStats = defaultUserStats();
    setUserStats(nextStats);
    if (result && canCalculate) {
      void calculateWithStats(nextStats);
    } else {
      setResult(null);
    }
  }, [calculateWithStats, canCalculate, result]);

  return {
    cardType,
    setCardType,
    position,
    setPosition,
    subPosition,
    setSubPosition,
    scorePosition,
    slotCount,
    skills,
    selections,
    selectedSkillIds,
    visibleStats,
    userStats,
    battingOrder,
    loadingSkills,
    calculating,
    canCalculate,
    error,
    result,
    updateSkill,
    updateLevel,
    updateUserStat,
    updateBattingOrder,
    resetUserStats,
    clearSlot,
    calculate,
  };
}
