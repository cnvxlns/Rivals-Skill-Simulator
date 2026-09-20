'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { calculateScore, fetchScoreSkills, fetchTicketExpectation } from './api';
import { canPlaceSkill, slotCountFor } from './cardRules';
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
  SkillLevelBonus,
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

/**
 * 카드 고유 능력치를 받아야 하는 스탯.
 *
 * 위의 스탯 입력은 육성·구단 관리를 반영한 값이지만, 일부 스킬은 "기본 주루+수비 합이
 * 155 이상인 경우"처럼 카드가 타고난 값에 임계를 건다(엘 그란데 등). 그 판정에만 쓰이므로
 * 실제로 필요한 스탯만 받는다. 비워 두면 서버가 표본 확률로 채점한다.
 */
const BASE_STAT_INPUTS: Record<Position, string[]> = {
  [Position.BATTER]: ['주루', '수비'],
  [Position.PITCHER]: [],
};

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

/**
 * 포지션특훈 보너스 칸 수. 게임이 레벨 6·12·20에서 하나씩 준다.
 *
 * 보너스는 스킬이 아니라 자리에 붙으므로 A·B가 함께 쓴다. 두 벌은 같은 자리에 무엇을
 * 끼울지 비교하는 것이라 자리의 훈련까지 갈리면 비교가 되지 않는다.
 */
export const TRAINING_BONUS_SLOTS = 3;

/** 보너스가 붙을 수 있는 스킬 풀. 모먼트 전용·HOF·블랙·WBC는 대상이 아니다. */
const TRAINING_BONUS_POOL = 'NORMAL';

const emptyTrainingBonuses = (): SkillLevelBonus[] =>
  Array.from({ length: TRAINING_BONUS_SLOTS }, () => ({ skillId: '', bonus: 1 }));

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
  // 포지션특훈이 이 자리에 붙여 준 스킬 레벨 보너스. 계정에 저장된 구단 설정과 섞지 않는다.
  const [trainingBonuses, setTrainingBonuses] = useState<SkillLevelBonus[]>(emptyTrainingBonuses);
  const [loadingSkills, setLoadingSkills] = useState(false);
  const [calculating, setCalculating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [userStats, setUserStats] = useState<Record<string, number>>(defaultUserStats);
  // 기본값을 두지 않는다. 비어 있음이 "모른다"는 뜻이고, 그때는 서버가 표본 확률을 쓴다.
  const [baseStats, setBaseStats] = useState<Record<string, number>>({});
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
  const visibleBaseStats = useMemo(() => BASE_STAT_INPUTS[position] ?? [], [position]);

  /** 보너스를 걸 수 있는 스킬. 아이언~골드만 나오므로 일반 풀로 좁힌다. */
  const trainingSkills = useMemo(
    () => skills.filter((skill) => skill.cardType === TRAINING_BONUS_POOL),
    [skills],
  );

  /** 실제로 채워진 보너스만 보낸다. 빈 칸은 요청에 넣지 않는다. */
  const activeTrainingBonuses = useMemo(
    () => trainingBonuses.filter((entry) => entry.skillId),
    [trainingBonuses],
  );

  /** 벌마다 이미 고른 스킬. 같은 벌 안에서만 중복을 막는다. */
  const selectedSkillIds = useMemo(
    () => sets.map((set) => set.selections.map((selection) => selection.skillId).filter(Boolean)),
    [sets],
  );

  /**
   * 이 카드에서는 나올 수 없는 스킬을 낀 벌이 있는가.
   *
   * 등급을 바꿔도 고른 스킬을 비우지 않으므로 새 카드의 풀 밖인 것이 남을 수 있다.
   * 스킬 ID 앞자리가 곧 풀이라 목록을 다시 받기 전에도 판단할 수 있다.
   */
  const unavailableSlots = useMemo(
    () =>
      sets.slice(0, activeCount).map((set) =>
        set.selections.map(
          (selection, index) =>
            !!selection.skillId &&
            !canPlaceSkill(
              cardGrade,
              cardVariant,
              index,
              selection.skillId,
              set.selections.filter((_, other) => other !== index).map((other) => other.skillId),
            ),
        ),
      ),
    [sets, activeCount, cardGrade, cardVariant],
  );
  const hasUnavailableSkill = unavailableSlots.some((set) => set.some(Boolean));

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

  /**
   * 포지션이 바뀌면 고른 스킬을 비운다.
   *
   * 등급·변형은 비우지 않는다 — 잘못 골랐다가 되돌릴 때 처음부터 다시 고르게 되는 것이
   * 가장 잦은 불편이라 라인업 편집기와 같이 유지한다. 새 카드에 없는 스킬은 슬롯마다
   * 경고로 알린다. 포지션은 사정이 다르다. 그 자리에서 아예 쓸 수 없는 스킬이 되므로
   * 남겨 두면 고칠 방법이 "하나씩 지우기"밖에 없다.
   */
  const lastPosition = useRef(scorePosition);
  useEffect(() => {
    if (lastPosition.current === scorePosition) return;
    lastPosition.current = scorePosition;
    setSets((prev) => prev.map(() => emptySet(slotCount)));
    // 보너스도 그 자리의 것이다. 자리가 바뀌면 남겨 둘 이유가 없고, 서버도 거절한다.
    setTrainingBonuses(emptyTrainingBonuses());
  }, [scorePosition, slotCount]);

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
        // 고른 스킬은 건드리지 않는다. 비우는 것은 포지션이 바뀔 때뿐이다(위 효과).
        clearResults();
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
  }, [cardGrade, cardVariant, scorePosition, clearResults]);

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

  /**
   * 보너스 한 칸을 고친다. 같은 스킬을 두 칸에 걸 수 없으므로 겹치면 먼저 있던 칸을 비운다.
   *
   * 게임에서도 한 자리의 보너스 셋에 같은 스킬이 나오지 않는다.
   */
  const updateTrainingBonus = useCallback((index: number, patch: Partial<SkillLevelBonus>) => {
    setTrainingBonuses((prev) =>
      prev.map((entry, idx) => {
        if (idx === index) return { ...entry, ...patch };
        if (patch.skillId && entry.skillId === patch.skillId) return { skillId: '', bonus: 1 };
        return entry;
      }),
    );
    clearResults();
  }, [clearResults]);

  const clearTrainingBonus = useCallback((index: number) => {
    setTrainingBonuses((prev) =>
      prev.map((entry, idx) => (idx === index ? { skillId: '', bonus: 1 } : entry)),
    );
    clearResults();
  }, [clearResults]);

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

  const calculateWithStats = useCallback(async (
    stats: Record<string, number>,
    base: Record<string, number> = baseStats,
  ) => {
    if (hasUnavailableSkill) {
      setError('skill_unavailable');
      return;
    }
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
      // 다 채웠을 때만 보낸다. 일부만 오면 서버가 어차피 표본 확률로 떨어진다.
      baseStats: Object.keys(base).length > 0 ? base : undefined,
      trainingBonuses: activeTrainingBonuses.length ? activeTrainingBonuses : undefined,
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
    battingOrder, pitcherSlot, canCalculate, hasUnavailableSkill, cardGrade, cardVariant,
    position, scorePosition, activeCount, throwHand, batHand, activeTrainingBonuses,
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
          // 보너스는 자리에 붙으므로 새로 뽑힌 스킬에도 걸린다. 서버가 그렇게 센다.
          trainingBonuses: activeTrainingBonuses.length ? activeTrainingBonuses : undefined,
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
    pitcherSlot, throwHand, batHand, userStats, lockSlotOne, activeTrainingBonuses,
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

  /**
   * 기본 능력치 한 칸을 고친다. 빈 칸(NaN)이면 키를 지워 "모른다"로 되돌린다.
   * 지우면 서버가 다시 표본 확률로 채점한다.
   */
  const updateBaseStat = useCallback((stat: string, value: number | null) => {
    const next = { ...baseStats };
    if (value == null || !Number.isFinite(value)) {
      delete next[stat];
    } else {
      next[stat] = Math.max(0, value);
    }
    setBaseStats(next);
    if (hasResult && canCalculate) {
      void calculateWithStats(userStats, next);
    } else {
      clearResults();
    }
  }, [baseStats, calculateWithStats, canCalculate, clearResults, hasResult, userStats]);

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
    trainingBonuses,
    trainingSkills,
    updateTrainingBonus,
    clearTrainingBonus,
    selectedSkillIds,
    visibleStats,
    userStats,
    visibleBaseStats,
    baseStats,
    updateBaseStat,
    battingOrder,
    pitcherSlot,
    loadingSkills,
    calculating,
    canCalculate,
    unavailableSlots,
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
