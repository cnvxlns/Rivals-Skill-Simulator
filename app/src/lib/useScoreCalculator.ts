'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { calculateScore, fetchScoreSkills, fetchTicketExpectation } from './api';
import { slotCountFor } from './cardRules';
import { defaultSubPosition } from './useScoreContext';
import {
  CardGrade,
  CardVariant,
  Handedness,
  Position,
  ScoreRequest,
  ScoreResponse,
  ScoreSelection,
  ScoreSkillOption,
  SubPosition,
  TicketExpectationResponse,
} from '../types';

/** 평균 타순 개념을 두지 않으므로 지정이 없으면 1번타자로 본다. 백엔드와 같은 전제다. */
const DEFAULT_BATTING_ORDER = 1;

/**
 * 빈 슬롯에 스킬을 처음 넣었을 때의 레벨.
 *
 * 점수표가 S 기준으로 채점하므로 계산기도 같은 지점에서 시작해야 두 화면의 숫자를
 * 바로 비교할 수 있다. levelLabels는 그 스킬이 실제로 갈 수 있는 등급만 담고 있어서,
 * S가 없으면(사다리가 D 하나뿐인 스킬 등) 백엔드와 같게 그 스킬의 최대 등급으로 내린다.
 */
const defaultLevelFor = (skill: ScoreSkillOption) => {
  const index = skill.levelLabels?.indexOf('S') ?? -1;
  return index >= 0 ? index + 1 : Math.max(1, skill.maxLevel);
};
const DEFAULT_USER_STAT = 120;
const DEFAULT_DECK_SCORE = 500;
const BATTER_STATS = ['파워', '정확', '선구', '인내', '주루', '수비'];
const PITCHER_STATS = ['구속', '변화', '구위', '제구', '지구력', '수비'];
const DECK_STATS = ['스페셜덱', '팀덱'];

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

/**
 * 스킬 한 벌과 그 결과.
 *
 * 카드 등급·포지션·타순·투타 방향·사용자 스탯은 A/B가 공유한다. 같은 조건에서 어떤
 * 스킬 조합이 나은지 보는 게 목적이라, 조건까지 갈리면 무엇 때문에 점수가 달라졌는지
 * 알 수 없다.
 */
export type ScoreSet = {
  selections: ScoreSlotSelection[];
  result: ScoreResponse | null;
};

const emptySelections = (slotCount: number): ScoreSlotSelection[] =>
  Array.from({ length: slotCount }, () => ({ skillId: '', level: 1 }));

const emptySet = (slotCount: number): ScoreSet => ({
  selections: emptySelections(slotCount),
  result: null,
});

/** A와 B. 비교를 꺼도 두 벌을 그대로 들고 있다가 다시 켜면 이어서 쓴다. */
export const SET_COUNT = 2;

export function useScoreCalculator() {
  const [cardGrade, setCardGrade] = useState<CardGrade>(CardGrade.SIGNATURE);
  const [cardVariant, setCardVariant] = useState<CardVariant>(CardVariant.NONE);
  const [position, setPosition] = useState<Position>(Position.BATTER);
  const [subPosition, setSubPosition] = useState<SubPosition | ''>(defaultSubPosition(Position.BATTER));
  const [skills, setSkills] = useState<ScoreSkillOption[]>([]);
  const [sets, setSets] = useState<ScoreSet[]>(() =>
    Array.from({ length: SET_COUNT }, () => emptySet(slotCountFor(CardGrade.SIGNATURE))),
  );
  const [compare, setCompare] = useState(false);
  // 스킬 변경권 기댓값. A 슬롯을 기준으로 본다. 비교를 켜도 기준은 A 하나다.
  const [tickets, setTickets] = useState<TicketExpectationResponse | null>(null);
  const [ticketsLoading, setTicketsLoading] = useState(false);
  const [lockSlotOne, setLockSlotOne] = useState(false);
  const [protectLevels, setProtectLevels] = useState<boolean[]>([]);
  const [loadingSkills, setLoadingSkills] = useState(false);
  const [calculating, setCalculating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [userStats, setUserStats] = useState<Record<string, number>>(defaultUserStats);
  const [battingOrder, setBattingOrder] = useState<number | null>(DEFAULT_BATTING_ORDER);
  const [pitcherSlot, setPitcherSlot] = useState<number | null>(null);
  const [throwHand, setThrowHand] = useState<Handedness>(Handedness.RIGHT);
  const [batHand, setBatHand] = useState<Handedness>(Handedness.RIGHT);

  const slotCount = useMemo(() => slotCountFor(cardGrade), [cardGrade]);
  /** 비교가 꺼져 있으면 A만 본다. */
  const activeCount = compare ? SET_COUNT : 1;

  /** 한 벌만 고친다. 나머지 벌은 신원을 유지한다. */
  const patchSet = useCallback((index: number, patch: (set: ScoreSet) => ScoreSet) => {
    setSets((prev) => prev.map((set, idx) => (idx === index ? patch(set) : set)));
  }, []);

  /** 조건이 바뀌면 모든 벌의 결과가 낡는다. */
  const clearResults = useCallback(() => {
    setSets((prev) => prev.map((set) => (set.result ? { ...set, result: null } : set)));
    // 조건이 바뀌면 변경권 기댓값도 그 조건의 값이 아니다.
    setTickets(null);
  }, []);

  /** 화면에 결과가 하나라도 떠 있는가. 스탯을 고칠 때 다시 계산할지 가른다. */
  const hasResult = sets.slice(0, activeCount).some((set) => set.result != null);

  // 계산은 부를 때의 최신 슬롯을 봐야 한다. deps에 sets를 넣으면 슬롯을 고칠 때마다
  // calculateWithStats의 신원이 바뀌어 스탯 재계산 경로가 불필요하게 다시 만들어진다.
  const setsRef = useRef(sets);
  setsRef.current = sets;
  const calcSeq = useRef(0);
  const scorePosition = subPosition;
  const visibleStats = useMemo(
    () => [...(position === Position.PITCHER ? PITCHER_STATS : BATTER_STATS), ...DECK_STATS],
    [position],
  );

  /** 벌마다 이미 고른 스킬. 같은 벌 안에서만 중복을 막는다. */
  const selectedSkillIds = useMemo(
    () => sets.map((set) => set.selections.map((selection) => selection.skillId).filter(Boolean)),
    [sets],
  );

  const canCalculate = useMemo(() => {
    const slotsFilled = sets
      .slice(0, activeCount)
      .every((set) => set.selections.length === slotCount && set.selections.every((s) => s.skillId));
    if (!slotsFilled) return false;
    if (!subPosition || subPosition === 'ALL') return false;

    if (position === Position.BATTER) {
      return battingOrder !== null && battingOrder >= 1 && battingOrder <= 9;
    } else {
      if (subPosition === 'SP') {
        return pitcherSlot !== null && pitcherSlot >= 1 && pitcherSlot <= 5;
      } else if (subPosition === 'RP') {
        return pitcherSlot !== null && pitcherSlot >= 1 && pitcherSlot <= 6;
      } else if (subPosition === 'CP') {
        return true;
      }
      return false;
    }
  }, [sets, activeCount, slotCount, subPosition, position, battingOrder, pitcherSlot]);

  useEffect(() => {
    setSets((prev) =>
      prev.map((set) => ({
        selections: Array.from({ length: slotCount }, (_, idx) => set.selections[idx] ?? { skillId: '', level: 1 }),
        result: null,
      })),
    );
  }, [slotCount]);

  useEffect(() => {
    setSubPosition(defaultSubPosition(position));
    setBattingOrder(DEFAULT_BATTING_ORDER);
    setPitcherSlot(null);
    clearResults();
  }, [position, clearResults]);

  useEffect(() => {
    if (!scorePosition) {
      setSkills([]);
      return;
    }
    let cancelled = false;

    const loadSkills = async () => {
      try {
        setLoadingSkills(true);
        setError(null);
        const loadedSkills = await fetchScoreSkills(cardGrade, cardVariant, scorePosition);
        if (cancelled) return;
        setSkills(loadedSkills);
        // 등급이나 포지션이 바뀌면 새 풀에 없는 스킬이 남을 수 있어 두 벌 모두 비운다.
        setSets((prev) => prev.map(() => emptySet(slotCount)));
      } catch {
        if (cancelled) return;
        setSkills([]);
        setSets((prev) => prev.map((set) => ({ ...set, result: null })));
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
  }, [cardGrade, cardVariant, scorePosition, slotCount]);

  const updateSkill = useCallback((setIndex: number, slotIndex: number, skillId: string) => {
    patchSet(setIndex, (set) => ({
      result: null,
      selections: set.selections.map((selection, idx) => {
        if (idx !== slotIndex) return selection;
        const nextSkill = skills.find((skill) => skill.skillId === skillId);
        if (!nextSkill) return { skillId, level: 1 };
        // 빈 슬롯에 처음 넣을 때만 기본값을 준다. 이미 고른 슬롯에서 스킬만 바꾸는 경우는
        // 사용자가 정한 레벨이므로 유지한다(넘치면 새 스킬의 최대 등급으로 내린다).
        return {
          skillId,
          level: selection.skillId
            ? Math.min(selection.level, nextSkill.maxLevel) || 1
            : defaultLevelFor(nextSkill),
        };
      }),
    }));
  }, [patchSet, skills]);

  const updateLevel = useCallback((setIndex: number, slotIndex: number, level: number) => {
    patchSet(setIndex, (set) => ({
      result: null,
      selections: set.selections.map((selection, idx) =>
        idx === slotIndex ? { ...selection, level } : selection,
      ),
    }));
  }, [patchSet]);

  const clearSlot = useCallback((setIndex: number, slotIndex: number) => {
    patchSet(setIndex, (set) => ({
      result: null,
      selections: set.selections.map((selection, idx) =>
        idx === slotIndex ? { skillId: '', level: 1 } : selection,
      ),
    }));
  }, [patchSet]);

  /**
   * 비교를 켜고 끈다.
   *
   * 켤 때 B를 A와 같게 채운다. 실제로 보고 싶은 건 "이 한 칸만 바꾸면 얼마나 달라지나"라서,
   * 빈 B로 시작하면 델타를 보기까지 슬롯을 다시 다 골라야 한다.
   */
  const toggleCompare = useCallback((next: boolean) => {
    setCompare(next);
    if (next) setSets((prev) => [prev[0], { selections: [...prev[0].selections], result: null }]);
  }, []);

  /** A의 스킬 구성을 B로 덮어쓴다. */
  const copyAToB = useCallback(() => {
    setSets((prev) => [prev[0], { selections: [...prev[0].selections], result: null }]);
  }, []);

  const calculateWithStats = useCallback(async (stats: Record<string, number>) => {
    if (!canCalculate) {
      setError('score_fill_slots');
      return;
    }

    const payloadFor = (set: ScoreSet): ScoreRequest => ({
      cardGrade,
      cardVariant,
      position: scorePosition,
      selections: set.selections.map<ScoreSelection>((selection) => ({
        skillId: selection.skillId,
        level: selection.level,
      })),
      battingOrder: position === Position.BATTER ? battingOrder : undefined,
      pitcherSlot: position === Position.PITCHER ? pitcherSlot : undefined,
      throwHand: position === Position.PITCHER ? throwHand : undefined,
      batHand: position === Position.BATTER ? batHand : undefined,
      userStats: stats,
    });

    // 늦게 온 이전 요청이 나중 결과를 덮지 않게 한다. 스탯을 연달아 고치면
    // 요청이 겹칠 수 있다.
    const seq = ++calcSeq.current;
    try {
      setCalculating(true);
      setError(null);
      const targets = setsRef.current.slice(0, activeCount);
      const results = await Promise.all(targets.map((set) => calculateScore(payloadFor(set))));
      if (seq !== calcSeq.current) return;
      setSets((prev) => prev.map((set, idx) => (idx < results.length ? { ...set, result: results[idx] } : set)));
    } catch {
      if (seq !== calcSeq.current) return;
      setSets((prev) => prev.map((set) => ({ ...set, result: null })));
      setError('score_error_calculate');
    } finally {
      if (seq === calcSeq.current) setCalculating(false);
    }
    // throwHand·batHand가 payload에 들어가므로 deps에도 있어야 한다. 빠져 있던 동안에는
    // 방향을 바꾸고 계산을 누르면 이전 방향으로 요청이 나갔다.
  }, [
    battingOrder, pitcherSlot, canCalculate, cardGrade, cardVariant,
    position, scorePosition, activeCount, throwHand, batHand,
  ]);

  // 조건이 바뀌면 화면에 남은 결과는 더 이상 그 조건의 결과가 아니다. updatePitcherSlot과
  // 같은 규칙을 투/타 방향에도 적용한다. 예전에는 raw setter라 낡은 결과가 남았다.
  const updateThrowHand = useCallback((value: Handedness) => {
    setThrowHand(value);
    clearResults();
  }, [clearResults]);

  const updateBatHand = useCallback((value: Handedness) => {
    setBatHand(value);
    clearResults();
  }, [clearResults]);

  const updatePitcherSlot = useCallback((value: number | null) => {
    const nextValue = value != null && value >= 1 && value <= 6 ? value : null;
    setPitcherSlot(nextValue);
    clearResults();
  }, [clearResults]);

  /**
   * 지금 A 슬롯을 기준으로 변경권을 몇 장 쓰면 나아지는지 계산한다.
   *
   * 버튼으로만 부른다. 2만 번을 뽑는 계산이라 슬롯을 고칠 때마다 자동으로 돌리면
   * 서버가 그만큼 일한다.
   */
  const evaluateTickets = useCallback(async () => {
    const filled = sets[0].selections.filter((selection) => selection.skillId);
    if (!filled.length || !scorePosition || scorePosition === 'ALL') return;
    setTicketsLoading(true);
    setError(null);
    try {
      setTickets(
        await fetchTicketExpectation({
          cardGrade,
          cardVariant,
          position: scorePosition,
          selections: filled.map((selection) => ({
            skillId: selection.skillId,
            level: selection.level,
          })),
          battingOrder: position === Position.BATTER ? battingOrder : undefined,
          pitcherSlot: position === Position.PITCHER ? pitcherSlot : undefined,
          throwHand: position === Position.PITCHER ? throwHand : undefined,
          batHand: position === Position.BATTER ? batHand : undefined,
          userStats,
          lockSlotOne,
          protectLevels,
        }),
      );
    } catch {
      setTickets(null);
      setError('score_error_calculate');
    } finally {
      setTicketsLoading(false);
    }
  }, [
    sets, scorePosition, cardGrade, cardVariant, position, battingOrder,
    pitcherSlot, throwHand, batHand, userStats, lockSlotOne, protectLevels,
  ]);

  const calculate = useCallback(async () => {
    await calculateWithStats(userStats);
  }, [calculateWithStats, userStats]);

  const updateUserStat = useCallback((stat: string, value: number) => {
    const nextValue = Number.isFinite(value) ? Math.max(0, value) : defaultValueForStat(stat);
    const nextStats = { ...userStats, [stat]: nextValue };
    setUserStats(nextStats);
    if (hasResult && canCalculate) {
      void calculateWithStats(nextStats);
    } else {
      clearResults();
    }
  }, [calculateWithStats, canCalculate, clearResults, hasResult, userStats]);

  const updateBattingOrder = useCallback((value: number | null) => {
    const nextValue = value != null && value >= 1 && value <= 9 ? value : null;
    setBattingOrder(nextValue);
    clearResults();
  }, [clearResults]);

  const resetUserStats = useCallback(() => {
    const nextStats = defaultUserStats();
    setUserStats(nextStats);
    if (hasResult && canCalculate) {
      void calculateWithStats(nextStats);
    } else {
      clearResults();
    }
  }, [calculateWithStats, canCalculate, clearResults, hasResult]);

  return {
    cardGrade,
    setCardGrade,
    cardVariant,
    setCardVariant,
    position,
    setPosition,
    subPosition,
    setSubPosition,
    scorePosition,
    slotCount,
    throwHand,
    setThrowHand: updateThrowHand,
    batHand,
    setBatHand: updateBatHand,
    skills,
    sets,
    compare,
    setCompare: toggleCompare,
    copyAToB,
    activeCount,
    hasResult,
    tickets,
    ticketsLoading,
    evaluateTickets,
    lockSlotOne,
    setLockSlotOne,
    protectLevels,
    setProtectLevels,
    selectedSkillIds,
    visibleStats,
    userStats,
    battingOrder,
    pitcherSlot,
    loadingSkills,
    calculating,
    canCalculate,
    error,
    updateSkill,
    updateLevel,
    updateUserStat,
    updateBattingOrder,
    updatePitcherSlot,
    resetUserStats,
    clearSlot,
    calculate,
  };
}
