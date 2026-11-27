'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { calculateScore, fetchScoreSkills, fetchTicketExpectation } from './api';
import { canPlaceSkill, skillPoolOf, slotCountFor } from './cardRules';
import type { LineupCalculatorInput } from './lineupCalculatorInput';
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
  TicketExpectationRequest,
  TicketExpectationResponse,
} from '../types';

/** 평균 타순 개념을 두지 않으므로 지정이 없으면 1번타자로 본다. 백엔드와 같은 전제다. */
const DEFAULT_BATTING_ORDER = 1;

/**
 * 계산기에서 직접 고르는 기본 레벨은 S까지다. 레벨 번호는 풀별 사다리의 순서를
 * 유지하므로 WBC·모먼트에서는 S가 1이다.
 */
const baseLevelsOnly = (skill: ScoreSkillOption): ScoreSkillOption => {
  const sIndex = skill.levelLabels?.indexOf('S') ?? -1;
  const maxLevel = Math.min(skill.maxLevel, sIndex < 0 ? 5 : sIndex + 1);
  return { ...skill, maxLevel };
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
 * 카드 등급·포지션·타순·투타 방향·사용자 스탯은 A/B가 공유한다.
 * 선발·중계 투수 슬롯은 각 조합에서 선택한다.
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

export type TicketScope = 'all' | 'lockFirst' | 'firstOnly';

type TicketCalculation = {
  request: TicketExpectationRequest;
  result?: TicketExpectationResponse;
  failed?: boolean;
};

export function useScoreCalculator(lineupInput?: LineupCalculatorInput) {
  const [cardGrade, setCardGrade] = useState<CardGrade>(lineupInput?.cardGrade ?? CardGrade.SIGNATURE);
  const [cardVariant, setCardVariant] = useState<CardVariant>(lineupInput?.cardVariant ?? CardVariant.NONE);
  const [position, setPosition] = useState<Position>(() =>
    lineupInput && ['SP', 'RP', 'CP'].includes(lineupInput.subPosition) ? Position.PITCHER : Position.BATTER,
  );
  const [subPosition, setSubPosition] = useState<SubPosition | ''>(() =>
    lineupInput?.subPosition ?? defaultSubPosition(position),
  );
  const [skills, setSkills] = useState<ScoreSkillOption[]>([]);
  const [sets, setSets] = useState<ScoreSet[]>(() => {
    const count = slotCountFor(cardGrade);
    const sets = Array.from({ length: SET_COUNT }, () => emptySet(count));
    if (lineupInput) {
      sets[0].selections = sets[0].selections.map((blank, index) => lineupInput.selections[index] ?? blank);
    }
    return sets;
  });
  const [compare, setCompare] = useState(false);
  // 스킬 변경권 기댓값. A 슬롯을 기준으로 본다. 비교를 켜도 기준은 A 하나다.
  const [ticketCalculation, setTicketCalculation] = useState<TicketCalculation | null>(null);
  const [selectedTicketScope, setTicketScope] = useState<TicketScope>('all');
  const [selectedMomentSkillId, setMomentSkillId] = useState('');
  const [trainingBonuses, setTrainingBonuses] = useState<SkillLevelBonus[]>(() =>
    emptyTrainingBonuses().map((blank, index) => lineupInput?.trainingBonuses?.[index] ?? blank),
  );
  const [transcendenceLevel, setTranscendenceLevel] = useState(lineupInput?.transcendenceLevel ?? undefined);
  const [loadingSkills, setLoadingSkills] = useState(false);
  const [calculating, setCalculating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [userStats, setUserStats] = useState<Record<string, number>>(() => ({
    ...defaultUserStats(),
    ...lineupInput?.userStats,
  }));
  // 기본값을 두지 않는다. 비어 있음이 "모른다"는 뜻이고, 그때는 서버가 표본 확률을 쓴다.
  const [baseStats, setBaseStats] = useState<Record<string, number>>(lineupInput?.baseStats ?? {});
  const [battingOrder, setBattingOrder] = useState<number | null>(lineupInput?.battingOrder ?? DEFAULT_BATTING_ORDER);
  const [pitcherSlots, setPitcherSlots] = useState<[number | null, number | null]>([lineupInput?.pitcherSlot ?? null, null]);
  const [pitcherSlotA] = pitcherSlots;
  const [throwHand, setThrowHand] = useState<Handedness>(lineupInput?.throwHand ?? Handedness.RIGHT);
  const [batHand, setBatHand] = useState<Handedness>(lineupInput?.batHand ?? Handedness.RIGHT);
  const calcSeq = useRef(0);

  const slotCount = useMemo(() => slotCountFor(cardGrade), [cardGrade]);
  // HOF·시그니처는 9초월에서 첫 슬롯 +1, 시그니처 블랙은 기본 +1에서 9초월 달성 시 +2가 된다.
  const canTranscendFirstSlot =
    cardGrade === CardGrade.HOF ||
    cardGrade === CardGrade.SIGNATURE ||
    cardGrade === CardGrade.SIGNATURE_BLACK;
  const baseFirstSlotBonus = cardGrade === CardGrade.SIGNATURE_BLACK ? 1 : 0;
  const nineTranscendenceBonus =
    cardGrade === CardGrade.SIGNATURE_BLACK ? 2 : canTranscendFirstSlot ? 1 : 0;
  const hasTranscended = (transcendenceLevel ?? 0) >= 9;
  const firstSlotBonus = hasTranscended ? nineTranscendenceBonus : baseFirstSlotBonus;
  /** 비교가 꺼져 있으면 A만 본다. */
  const activeCount = compare ? SET_COUNT : 1;

  /** 한 벌만 고친다. 나머지 벌은 신원을 유지한다. */
  const patchSet = useCallback((index: number, patch: (set: ScoreSet) => ScoreSet) => {
    setSets((prev) => prev.map((set, idx) => (idx === index ? patch(set) : set)));
  }, []);

  /** 조건이 바뀌면 모든 벌의 결과가 낡는다. */
  const clearResults = useCallback(() => {
    calcSeq.current++;
    setCalculating(false);
    setSets((prev) => prev.map((set) => (set.result ? { ...set, result: null } : set)));
  }, []);

  /** 화면에 결과가 하나라도 떠 있는가. 스탯을 고칠 때 다시 계산할지 가른다. */
  const hasResult = sets.slice(0, activeCount).some((set) => set.result != null);

  // 계산은 부를 때의 최신 슬롯을 봐야 한다. deps에 sets를 넣으면 슬롯을 고칠 때마다
  // calculateWithStats의 신원이 바뀌어 스탯 재계산 경로가 불필요하게 다시 만들어진다.
  const setsRef = useRef(sets);
  setsRef.current = sets;
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

  /** 일반 모먼트 카드에서 첫 슬롯 고유 스킬로 지정할 수 있는 모먼트 전용 스킬 목록. */
  const momentSkills = useMemo(
    () => skills.filter((skill) => skill.cardType === 'MOMENT'),
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
  const requiresPitcherSlot = position === Position.PITCHER && (subPosition === 'SP' || subPosition === 'RP');
  const maxPitcherSlot = subPosition === 'SP' ? 5 : 6;
  const hasValidPitcherSlots = !requiresPitcherSlot || pitcherSlots.slice(0, activeCount).every(
    (slot) => slot !== null && slot >= 1 && slot <= maxPitcherSlot,
  );

  const canCalculate = useMemo(() => {
    const slotsFilled = sets
      .slice(0, activeCount)
      .every((set) => set.selections.length === slotCount && set.selections.every((s) => s.skillId));
    if (!slotsFilled) return false;
    if (!subPosition || subPosition === 'ALL') return false;

    if (position === Position.BATTER) {
      return battingOrder !== null && battingOrder >= 1 && battingOrder <= 9;
    }
    return subPosition === 'CP' || (requiresPitcherSlot && hasValidPitcherSlots);
  }, [sets, activeCount, slotCount, subPosition, position, battingOrder, requiresPitcherSlot, hasValidPitcherSlots]);

  useEffect(() => {
    setSets((prev) =>
      prev.map((set) => ({
        selections: Array.from({ length: slotCount }, (_, idx) => set.selections[idx] ?? { skillId: '', level: 1 }),
        result: null,
      })),
    );
  }, [slotCount]);

  const changePosition = useCallback((next: Position) => {
    if (next === position) return;
    setPosition(next);
    setSubPosition(defaultSubPosition(next));
    setBattingOrder(DEFAULT_BATTING_ORDER);
    setPitcherSlots([null, null]);
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
    setMomentSkillId('');
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
        setSkills(loadedSkills.map(baseLevelsOnly));
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
    if (setIndex === 0 && slotIndex === 0 && skillId && skillPoolOf(skillId) === 'MOMENT') {
      setMomentSkillId(skillId);
    }
    patchSet(setIndex, (set) => ({
      result: null,
      selections: set.selections.map((selection, idx) => {
        if (idx !== slotIndex || selection.skillId === skillId) return selection;
        const nextSkill = skills.find((skill) => skill.skillId === skillId);
        if (!nextSkill) return { skillId, level: 1 };
        if (!selection.skillId) return { skillId, level: nextSkill.maxLevel };
        const previousSkill = skills.find((skill) => skill.skillId === selection.skillId);
        // 이전 카드 등급·변형에서만 쓰이던 스킬을 교체할 때는 S(최대 기본 등급)로 시작한다.
        // WBC·모먼트의 level=1(S)이 일반 스킬의 level=1(D)로 잘못 옮겨지지 않게 한다.
        if (!previousSkill) return { skillId, level: nextSkill.maxLevel };
        const previousLabel = previousSkill.levelLabels?.[selection.level - 1];
        const sameLevel = previousLabel ? (nextSkill.levelLabels?.indexOf(previousLabel) ?? -1) + 1 : 0;
        return { skillId, level: Math.min(sameLevel || selection.level, nextSkill.maxLevel) };
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

  const updateTranscendence = useCallback((achieved: boolean) => {
    if (achieved === hasTranscended) return;
    setTranscendenceLevel(achieved ? 9 : undefined);
    clearResults();
  }, [hasTranscended, clearResults]);

  const selectCardGrade = useCallback((grade: CardGrade) => {
    if (grade === cardGrade) return;
    setCardGrade(grade);
    clearResults();
  }, [cardGrade, clearResults]);

  const selectCardVariant = useCallback((variant: CardVariant) => {
    if (variant === cardVariant) return;
    setCardVariant(variant);
    clearResults();
  }, [cardVariant, clearResults]);

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
    calcSeq.current++;
    setCalculating(false);
    setCompare(next);
    if (next) {
      setSets((prev) => [prev[0], { selections: [...prev[0].selections], result: null }]);
      setPitcherSlots(([slotA]) => [slotA, slotA]);
    }
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
      setError(hasValidPitcherSlots ? 'score_fill_slots' : 'score_fill_pitcher_slots');
      return;
    }

    const payloadFor = (set: ScoreSet, setIndex: number): ScoreRequest => ({
      cardGrade,
      cardVariant,
      position: scorePosition,
      selections: set.selections.map<ScoreSelection>((selection) => ({
        skillId: selection.skillId,
        level: selection.level,
      })),
      battingOrder: position === Position.BATTER ? battingOrder : undefined,
      pitcherSlot: position === Position.PITCHER ? pitcherSlots[setIndex] : undefined,
      throwHand: position === Position.PITCHER ? throwHand : undefined,
      batHand: position === Position.BATTER ? batHand : undefined,
      userStats: stats,
      // 다 채웠을 때만 보낸다. 일부만 오면 서버가 어차피 표본 확률로 떨어진다.
      baseStats: Object.keys(base).length > 0 ? base : undefined,
      trainingBonuses: activeTrainingBonuses.length ? activeTrainingBonuses : undefined,
      transcendenceLevel,
    });

    // 늦게 온 이전 요청이 나중 결과를 덮지 않게 한다. 스탯을 연달아 고치면
    // 요청이 겹칠 수 있다.
    const seq = ++calcSeq.current;
    try {
      setCalculating(true);
      setError(null);
      const targets = setsRef.current.slice(0, activeCount);
      const results = await Promise.all(targets.map((set, index) => calculateScore(payloadFor(set, index))));
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
    battingOrder, pitcherSlots, canCalculate, hasValidPitcherSlots, hasUnavailableSkill, cardGrade, cardVariant,
    position, scorePosition, activeCount, throwHand, batHand, activeTrainingBonuses, baseStats, transcendenceLevel,
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

  const updatePitcherSlot = useCallback((setIndex: number, value: number | null) => {
    const nextValue = value != null && value >= 1 && value <= 6 ? value : null;
    setPitcherSlots(([slotA, slotB]) => setIndex === 0 ? [nextValue, slotB] : [slotA, nextValue]);
    clearResults();
  }, [clearResults]);

  const ticketSelections = sets[0].selections;
  const firstSkill = ticketSelections[0]?.skillId;
  const firstSlotMomentSkillId = firstSkill && skillPoolOf(firstSkill) === 'MOMENT' ? firstSkill : '';
  const validSelectedMomentSkillId = momentSkills.some((skill) => skill.skillId === selectedMomentSkillId)
    ? selectedMomentSkillId
    : '';
  const effectiveMomentSkillId =
    cardGrade === CardGrade.MOMENT ? firstSlotMomentSkillId || validSelectedMomentSkillId : '';
  const firstSlotOptionsAvailable = !!firstSkill && (
    cardGrade === CardGrade.SIGNATURE || cardGrade === CardGrade.PRIME || cardGrade === CardGrade.IMPACT || (
      (cardGrade === CardGrade.MOMENT || cardGrade === CardGrade.SUPREME_MOMENT) && !!firstSlotMomentSkillId
    )
  );
  const ticketScope = firstSlotOptionsAvailable ? selectedTicketScope : 'all';
  const ticketHasUnavailableSkill = unavailableSlots[0].some(Boolean);
  const ticketRequest = useMemo<TicketExpectationRequest | null>(() => {
    if (!scorePosition || scorePosition === 'ALL' || ticketHasUnavailableSkill) return null;
    if (!ticketSelections.some((selection) => selection.skillId)) return null;
    if (cardGrade === CardGrade.MOMENT && !effectiveMomentSkillId) return null;
    const selections = ticketSelections.map((selection) => selection.skillId ? { ...selection } : null);
    while (selections[selections.length - 1] === null) selections.pop();
    return {
      cardGrade,
      cardVariant,
      position: scorePosition,
      selections,
      battingOrder: position === Position.BATTER ? battingOrder : undefined,
      pitcherSlot: position === Position.PITCHER ? pitcherSlotA : undefined,
      throwHand: position === Position.PITCHER ? throwHand : undefined,
      batHand: position === Position.BATTER ? batHand : undefined,
      userStats,
      baseStats: Object.keys(baseStats).length > 0 ? baseStats : undefined,
      lockSlotOne: ticketScope === 'lockFirst',
      rerollFirstSlotOnly: ticketScope === 'firstOnly' || undefined,
      trainingBonuses: activeTrainingBonuses.length ? activeTrainingBonuses : undefined,
      transcendenceLevel,
      momentSkillId: cardGrade === CardGrade.MOMENT ? effectiveMomentSkillId : undefined,
    };
  }, [
    ticketSelections, ticketHasUnavailableSkill, scorePosition, cardGrade, cardVariant, position,
    battingOrder, pitcherSlotA, throwHand, batHand, userStats, baseStats, ticketScope, activeTrainingBonuses,
    transcendenceLevel, effectiveMomentSkillId,
  ]);

  // 요청 당시의 A 슬롯과 설정이 화면과 같을 때만 응답을 표시한다.
  const currentTicketCalculation = ticketCalculation?.request === ticketRequest ? ticketCalculation : null;
  const tickets = currentTicketCalculation?.result ?? null;
  const ticketsLoading = !!currentTicketCalculation && !tickets && !currentTicketCalculation.failed;

  const evaluateTickets = useCallback(async () => {
    if (!ticketRequest) return;
    const pending = { request: ticketRequest };
    setTicketCalculation(pending);
    try {
      const result = await fetchTicketExpectation(ticketRequest);
      if (ticketRequest.rerollFirstSlotOnly && result.tickets.some((outcome) => !outcome.betterCombinations)) {
        throw new Error('This server does not support first-slot-only ticket calculations.');
      }
      setTicketCalculation((current) => current === pending ? { ...pending, result } : current);
    } catch {
      setTicketCalculation((current) => current === pending ? { ...pending, failed: true } : current);
    }
  }, [ticketRequest]);

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
    setCardGrade: selectCardGrade,
    cardVariant,
    setCardVariant: selectCardVariant,
    position,
    setPosition: changePosition,
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
    ticketError: currentTicketCalculation?.failed ?? false,
    canEvaluateTickets: ticketRequest != null,
    evaluateTickets,
    firstSlotOptionsAvailable,
    ticketScope,
    setTicketScope,
    momentSkills,
    momentSkillId: effectiveMomentSkillId,
    setMomentSkillId,
    firstSlotHasMomentSkill: !!firstSlotMomentSkillId,
    trainingBonuses,
    trainingSkills,
    updateTrainingBonus,
    clearTrainingBonus,
    hasTranscended,
    canTranscendFirstSlot,
    baseFirstSlotBonus,
    nineTranscendenceBonus,
    firstSlotBonus,
    updateTranscendence,
    selectedSkillIds,
    visibleStats,
    userStats,
    visibleBaseStats,
    baseStats,
    updateBaseStat,
    battingOrder,
    pitcherSlots,
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
