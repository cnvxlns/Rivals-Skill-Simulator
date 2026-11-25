'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { apiErrorMessage, createDeck, fetchDeck, scoreDeck, updateDeck } from './api';
import { canPlaceSkill, skillPoolsFor, slotCountFor } from './cardRules';
import {
  BENCH_SLOTS,
  BenchTeamBuffSkill,
  BullpenTactic,
  CardGrade,
  CardVariant,
  ChaseTactic,
  DeckDetail,
  DeckImportWarning,
  DeckPlayer,
  DeckRules,
  DeckSaveRequest,
  DeckScoreChoice,
  DeckScoreLadder,
  DeckScoreResponse,
  DeckScoreSide,
  LINEUP_SLOTS,
  PITCHER_COUNT,
  PositionTraining,
  RelieverRole,
  WinTactic,
} from '../types';

/** 투수 세 자리 중 어느 것을 고르는가. */
export type PitcherField = 'starters' | 'relievers' | 'closers';

/**
 * 각 자리에서 고를 수 있는 값의 범위.
 *
 * 셋을 서로 묶지 않고 각자 자유롭게 고르게 한다. 합이 12가 아니면 화면이 경고를 띄우고
 * 저장을 막는다. 중계 4~7은 선발 4~6과 마무리 1~2에서 나오는 폭이다.
 */
export const PITCHER_RANGES: Record<PitcherField, number[]> = {
  starters: [4, 5, 6],
  relievers: [4, 5, 6, 7],
  closers: [1, 2],
};

const relieverSlotsFor = (count: number): string[] =>
  Array.from({ length: count }, (_, index) => `RP${index + 1}`);

export const pitcherSlotsFor = (starters: number, relievers: number, closers: number): string[] => [
  ...Array.from({ length: starters }, (_, i) => `SP${i + 1}`),
  ...relieverSlotsFor(relievers),
  ...Array.from({ length: closers }, (_, i) => `CP${i + 1}`),
];

export const isBench = (slot: string) => (BENCH_SLOTS as readonly string[]).includes(slot);
export const isLineup = (slot: string) => (LINEUP_SLOTS as readonly string[]).includes(slot);
export const isReliever = (slot: string) => slot.startsWith('RP');
export const isPitcher = (slot: string) => /^(SP|RP|CP)\d+$/.test(slot);

export const RELIEVER_ROLES = [RelieverRole.WIN, RelieverRole.CHASE, RelieverRole.LONG] as const;
export const WIN_DIVISION_MIN = 3;
export const CHASE_AGGRESSIVE_MIN = 2;

export function benchTeamBuffSkillsFor(
  rules: DeckRules | null | undefined,
  player: Pick<DeckPlayer, 'cardGrade' | 'cardVariant'>,
): BenchTeamBuffSkill[] {
  const pools = skillPoolsFor(player.cardGrade, player.cardVariant);
  return (rules?.benchTeamBuffSkills ?? []).filter((skill) =>
    pools.some((pool) => pool === skill.cardType),
  );
}

// 슬롯 수 규칙은 카드 축의 다른 규칙과 함께 둔다. 여기서 다시 내보내는 것은 이미 이
// 모듈에서 가져다 쓰는 화면이 있어서다.
export { slotCountFor };

/** 스킬 목록을 조회할 때 쓰는 포지션. */
export const positionForSlot = (slot: string, benchPosition?: string): string => {
  if (isLineup(slot)) return slot;
  if (isPitcher(slot)) return slot.replace(/\d+$/, '');
  return benchPosition || 'C';
};

/** 아직 스킬을 다 고르지 않은 자리인가. */
export const isPlayerComplete = (player: DeckPlayer | undefined): boolean => {
  if (!player) return false;
  if (isBench(player.slot)) return !!player.cardGrade;
  const needed = slotCountFor(player.cardGrade);
  return player.skills.length === needed && player.skills.every((s) => s.skillId);
};

/**
 * 이 카드에서는 나올 수 없는 스킬을 끼고 있는가.
 *
 * 등급을 바꿔도 고른 스킬을 지우지 않으므로 남은 스킬이 새 카드의 풀 밖일 수 있다.
 * 스킬 ID 앞자리만 보면 알 수 있어 스킬 목록을 받아 두지 않아도 된다.
 */
export const unavailableSkills = (player: DeckPlayer | undefined): string[] => {
  if (!player) return [];
  const ids = player.skills.map((s) => s.skillId);
  return ids.filter(
    (skillId, index) =>
      !canPlaceSkill(
        String(player.cardGrade),
        player.cardVariant ? String(player.cardVariant) : undefined,
        index,
        skillId,
        ids.filter((_, other) => other !== index),
      ),
  );
};

/** 한 자리의 스킬이 게임 규칙을 어기는 방식. 여럿일 수 있다. */
export type SkillProblem =
  /** 이 카드에서는 나올 수 없는 스킬. */
  | { kind: 'UNAVAILABLE'; index: number; skillId: string }
  /** 같은 스킬을 두 번 들고 있다. */
  | { kind: 'DUPLICATE'; index: number; skillId: string }
  /** 이 카드의 칸 수를 넘겼다. */
  | { kind: 'OVERFLOW'; index: number; skillId: string };

/**
 * 이 자리의 스킬이 어긴 규칙.
 *
 * 워크북은 게임 규칙을 검사하지 않아서 중복이나 칸 초과가 그대로 들어온다(배포된 샘플부터
 * 전원이 같은 스킬을 네 칸에 들고 있다). 임포트가 조용히 손보지 않으므로 여기서 짚어 준다.
 */
export const skillProblemsOf = (player: DeckPlayer | undefined): SkillProblem[] => {
  if (!player) return [];
  const ids = player.skills.map((s) => s.skillId);
  const slotCount = slotCountFor(player.cardGrade);
  const out: SkillProblem[] = [];
  ids.forEach((skillId, index) => {
    if (index >= slotCount) {
      out.push({ kind: 'OVERFLOW', index, skillId });
      return;
    }
    if (ids.indexOf(skillId) !== index) {
      out.push({ kind: 'DUPLICATE', index, skillId });
      return;
    }
    const others = ids.filter((_, other) => other !== index);
    if (
      !canPlaceSkill(
        String(player.cardGrade),
        player.cardVariant ? String(player.cardVariant) : undefined,
        index,
        skillId,
        others,
      )
    ) {
      out.push({ kind: 'UNAVAILABLE', index, skillId });
    }
  });
  return out;
};

const emptyPlayer = (slot: string, battingOrder?: number): DeckPlayer => ({
  slot,
  cardGrade: CardGrade.SIGNATURE,
  cardVariant: CardVariant.NONE,
  skills: [],
  ...(battingOrder ? { battingOrder } : {}),
  ...(isReliever(slot) ? { relieverRole: RelieverRole.LONG } : {}),
});

/** 같은 이동 규칙을 사용해 레인에서 미리 보여 주고, 드롭할 때 덱에 반영한다. */
export function moveRelieverRows<T extends { role: RelieverRole }>(
  rows: readonly T[],
  fromIndex: number,
  targetRole: RelieverRole,
  targetIndexInRole: number,
): readonly T[] {
  const moving = rows[fromIndex];
  if (!moving) return rows;

  const groups = {
    [RelieverRole.WIN]: rows.filter(({ role }) => role === RelieverRole.WIN),
    [RelieverRole.CHASE]: rows.filter(({ role }) => role === RelieverRole.CHASE),
    [RelieverRole.LONG]: rows.filter(({ role }) => role === RelieverRole.LONG),
  };
  const source = groups[moving.role];
  const target = groups[targetRole];
  const sourceIndex = source.indexOf(moving);

  if (source !== target && source.length === 1) {
    const swapIndex = Math.min(Math.max(targetIndexInRole, 0), target.length - 1);
    const swapped = target[swapIndex];
    if (!swapped) return rows;
    source[sourceIndex] = { ...swapped, role: moving.role };
    target[swapIndex] = { ...moving, role: targetRole };
  } else {
    source.splice(sourceIndex, 1);
    const insertIndex = Math.min(Math.max(targetIndexInRole, 0), target.length);
    if (source === target && sourceIndex === insertIndex) return rows;
    target.splice(insertIndex, 0, source === target ? moving : { ...moving, role: targetRole });
  }
  return RELIEVER_ROLES.flatMap((role) => groups[role]);
}

function assignRelieverSlots(
  players: Record<string, DeckPlayer>,
  rows: readonly { player: DeckPlayer; role: RelieverRole }[],
): Record<string, DeckPlayer> {
  const next = { ...players };
  rows.forEach(({ player, role }, index) => {
    const slot = `RP${index + 1}`;
    next[slot] = player.slot === slot && player.relieverRole === role
      ? player
      : {
          ...player,
          slot,
          relieverRole: role,
          pitcherSlot: index + 1,
          // 위치 훈련 차이를 계산할 수 있도록 입력 당시의 자리를 유지한다.
          statsSlot: player.statsSlot ?? (player.slot !== slot && player.stats ? player.slot : undefined),
        };
  });
  return next;
}

function normalizeRelieverGroup(players: Record<string, DeckPlayer>, count: number) {
  const relievers = relieverSlotsFor(count).map((slot) => players[slot]);
  if (relievers.every((player) => !player.relieverRole || player.relieverRole === RelieverRole.LONG)) {
    const win = Math.floor(count / 2);
    return assignRelieverSlots(players, relievers.map((player, index) => ({
      player,
      role: index < win ? RelieverRole.WIN
        : index < count - 1 ? RelieverRole.CHASE
        : RelieverRole.LONG,
    })));
  }

  const groups: Record<RelieverRole, DeckPlayer[]> = {
    [RelieverRole.WIN]: [],
    [RelieverRole.CHASE]: [],
    [RelieverRole.LONG]: [],
  };
  relievers.forEach((player) => groups[player.relieverRole ?? RelieverRole.LONG].push(player));
  RELIEVER_ROLES.forEach((role) => {
    if (groups[role].length > 0) return;
    const donor = RELIEVER_ROLES.reduce((largest, next) =>
      groups[next].length > groups[largest].length ? next : largest,
    );
    const player = groups[donor].pop();
    if (player) groups[role].push(player);
  });
  return assignRelieverSlots(
    players,
    RELIEVER_ROLES.flatMap((role) => groups[role].map((player) => ({ player, role }))),
  );
}

type RosterEditorState = {
  starterCount: number;
  relieverCount: number;
  closerCount: number;
  players: Record<string, DeckPlayer>;
  winTactic: WinTactic;
  chaseTactic: ChaseTactic;
};

function validateTactics(state: RosterEditorState): RosterEditorState {
  const relievers = relieverSlotsFor(state.relieverCount).map((slot) => state.players[slot]);
  return {
    ...state,
    winTactic: state.winTactic === BullpenTactic.DIVISION &&
      relievers.filter(({ relieverRole }) => relieverRole === RelieverRole.WIN).length < WIN_DIVISION_MIN
      ? BullpenTactic.DEFAULT : state.winTactic,
    chaseTactic: state.chaseTactic === BullpenTactic.AGGRESSIVE &&
      relievers.filter(({ relieverRole }) => relieverRole === RelieverRole.CHASE).length < CHASE_AGGRESSIVE_MIN
      ? BullpenTactic.DEFAULT : state.chaseTactic,
  };
}

function normalizeRoster(state: RosterEditorState): RosterEditorState {
  const { starterCount, closerCount, relieverCount } = state;
  const players: Record<string, DeckPlayer> = {};
  [...LINEUP_SLOTS, ...BENCH_SLOTS, ...pitcherSlotsFor(starterCount, relieverCount, closerCount)]
    .forEach((slot, index) => {
      players[slot] = state.players[slot] ?? emptyPlayer(
        slot, index < LINEUP_SLOTS.length ? index + 1 : undefined,
      );
    });
  return validateTactics({ ...state, players: normalizeRelieverGroup(players, relieverCount) });
}

/**
 * 덱 편집 상태.
 *
 * 자리(slot)를 키로 하는 맵으로 들고 있다가 저장할 때 배열로 편다. 선발·마무리 수를
 * 바꾸면 투수 자리 이름이 통째로 달라지는데, 맵이면 남는 자리만 버리고 이미 채운 자리는
 * 그대로 살릴 수 있다.
 */
/**
 * @param training 구단의 포지션 훈련. 미리보기 채점은 인증이 없어 계정 설정을 읽을 수
 *   없으므로 화면이 들고 있는 값을 실어 보낸다. 저장된 덱은 서버가 직접 읽는다.
 */
export function useDeckEditor(initial?: DeckDetail, training?: PositionTraining) {
  const [name, setName] = useState(initial?.name ?? '');
  const [roster, setRoster] = useState<RosterEditorState>(() => {
    const starterCount = initial?.roster.starterCount ?? 5;
    const closerCount = initial?.roster.closerCount ?? 2;
    return normalizeRoster({
      starterCount,
      closerCount,
      relieverCount: PITCHER_COUNT - starterCount - closerCount,
      players: Object.fromEntries(initial?.roster.players.map((player) => [player.slot, player]) ?? []),
      winTactic: initial?.roster.winTactic ?? BullpenTactic.DEFAULT,
      chaseTactic: initial?.roster.chaseTactic ?? BullpenTactic.DEFAULT,
    });
  });
  const { starterCount, closerCount, relieverCount, players, winTactic, chaseTactic } = roster;

  /**
   * 덱 스코어 보상에서 고른 칸들. 임계값마다 좌·우 중 하나다.
   *
   * 키는 `TEAM:200`처럼 사다리와 임계값을 합친 것이다. 맵으로 들고 있으면 같은 칸을 두 번
   * 고르는 상태가 애초에 만들어지지 않는다.
   */
  const [coordination, setCoordination] = useState<Record<string, DeckScoreChoice>>(() => {
    const seed: Record<string, DeckScoreChoice> = {};
    initial?.roster.deckScoreChoices?.forEach((choice) => {
      seed[`${choice.ladder}:${choice.threshold}`] = choice;
    });
    return seed;
  });

  const [importWarnings, setImportWarnings] = useState<DeckImportWarning[]>([]);

  const [score, setScore] = useState<DeckScoreResponse | null>(initial?.score ?? null);
  const [scoring, setScoring] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pitcherCounts: Record<PitcherField, number> = {
    starters: starterCount,
    relievers: relieverCount,
    closers: closerCount,
  };

  const pitcherTotal = starterCount + relieverCount + closerCount;
  const isPitcherStaffValid = pitcherTotal === PITCHER_COUNT;

  /** 세 자리를 각자 고른다. 서로 건드리지 않는다. */
  const setPitcherCount = useCallback((field: PitcherField, value: number) => {
    const key = field === 'starters' ? 'starterCount'
      : field === 'relievers' ? 'relieverCount' : 'closerCount';
    setRoster((prev) => prev[key] === value ? prev : normalizeRoster({ ...prev, [key]: value }));
  }, []);
  const pitcherSlots = useMemo(
    () => pitcherSlotsFor(starterCount, relieverCount, closerCount),
    [starterCount, relieverCount, closerCount],
  );
  const allSlots = useMemo(
    () => [...LINEUP_SLOTS, ...BENCH_SLOTS, ...pitcherSlots],
    [pitcherSlots],
  );

  const completedCount = allSlots.filter((slot) => isPlayerComplete(players[slot])).length;
  /**
   * 이 카드에서 나올 수 없는 스킬을 낀 자리.
   *
   * 백엔드도 같은 것을 막지만 그쪽 오류는 저장을 눌러야 보인다. 어느 자리가 문제인지
   * 미리 알려 주고 채점·저장을 멈춘다.
   */
  const skillProblems = useMemo(() => {
    const out: Record<string, SkillProblem[]> = {};
    allSlots.forEach((slot) => {
      const problems = skillProblemsOf(players[slot]);
      if (problems.length) out[slot] = problems;
    });
    return out;
  }, [allSlots, players]);

  const brokenSlots = useMemo(() => Object.keys(skillProblems), [skillProblems]);
  // 합이 12가 아니면 자리 수 자체가 26이 아니므로 저장도 채점도 할 수 없다.
  const isComplete =
    completedCount === allSlots.length && isPitcherStaffValid && brokenSlots.length === 0;

  const updatePlayer = useCallback((slot: string, patch: Partial<DeckPlayer>) => {
    setRoster((prev) => {
      const current = prev.players[slot];
      if (!current) return prev;
      const player = { ...current, ...patch, slot };
      if (
        isBench(slot) &&
        (player.cardGrade !== current.cardGrade || player.cardVariant !== current.cardVariant)
      ) {
        const kept: DeckPlayer['skills'] = [];
        const maxSlots = slotCountFor(player.cardGrade);
        for (const skill of player.skills) {
          const selected = kept.map(({ skillId }) => skillId);
          if (kept.length >= maxSlots) break;
          if (
            !selected.includes(skill.skillId) &&
            canPlaceSkill(player.cardGrade, player.cardVariant, kept.length, skill.skillId, selected)
          ) {
            kept.push(skill);
          }
        }
        player.skills = kept;
      }
      return { ...prev, players: { ...prev.players, [slot]: player } };
    });
  }, []);

  /**
   * 타순을 바꾼다. 그 자리를 뽑아 목표 번호에 끼워 넣고 나머지를 한 칸씩 민다.
   *
   * 맞바꾸기가 아니다. 3번을 1번으로 옮기면 기존 1·2번이 2·3번으로 밀린다. 실제 라인업을
   * 짜는 감각에 가깝고, 여러 명을 연달아 옮길 때도 의도대로 움직인다.
   *
   * 주전 9명이 1~9를 한 번씩 써야 저장이 통과하는데, 순열을 통째로 다시 매기므로
   * 중간에 중복이나 구멍이 생기지 않는다.
   */
  const moveBattingOrder = useCallback((slot: string, order: number) => {
    setRoster((state) => {
      const prev = state.players;
      if (!isLineup(slot) || !prev[slot]?.battingOrder) return state;
      const target = Math.min(Math.max(order, 1), LINEUP_SLOTS.length);
      // 타순이 없는 자리는 뒤로 민다. 저장된 덱이 깨져 있어도 9행이 유지된다.
      const ordered: string[] = [...LINEUP_SLOTS].sort(
        (a, b) => (prev[a]?.battingOrder ?? 99) - (prev[b]?.battingOrder ?? 99),
      );
      const from = ordered.indexOf(slot);
      if (from < 0 || from === target - 1) return state;
      ordered.splice(target - 1, 0, ...ordered.splice(from, 1));

      // 번호가 실제로 바뀐 자리만 새 객체로 만든다. 손대지 않은 선수의 신원이 유지돼야
      // toRequest가 덜 흔들리고, 완성된 덱에서 프리뷰 채점이 덜 나간다.
      const next = { ...prev };
      ordered.forEach((each, index) => {
        if (prev[each]?.battingOrder !== index + 1) {
          next[each] = { ...prev[each], battingOrder: index + 1 };
        }
      });
      return { ...state, players: next };
    });
  }, []);

  const moveReliever = useCallback((fromIndex: number, targetRole: RelieverRole, targetIndex: number) => {
    setRoster((prev) => {
      const rows = relieverSlotsFor(prev.relieverCount).map((slot) => {
        const player = prev.players[slot];
        return { player, role: player.relieverRole ?? RelieverRole.LONG };
      });
      const moved = moveRelieverRows(rows, fromIndex, targetRole, targetIndex);
      return moved === rows ? prev : validateTactics({
        ...prev,
        players: assignRelieverSlots(prev.players, moved),
      });
    });
  }, []);

  const setWinTactic = useCallback((winTactic: WinTactic) => {
    setRoster((prev) => validateTactics({ ...prev, winTactic }));
  }, []);

  const setChaseTactic = useCallback((chaseTactic: ChaseTactic) => {
    setRoster((prev) => validateTactics({ ...prev, chaseTactic }));
  }, []);

  /** 타순 1~9 순서로 편 주전 자리. 타순 레인이 이 순서로 행을 그린다. */
  const lineupByBattingOrder = useMemo(
    (): string[] =>
      [...LINEUP_SLOTS].sort(
        (a, b) => (players[a]?.battingOrder ?? 99) - (players[b]?.battingOrder ?? 99),
      ),
    [players],
  );

  const toRequest = useCallback(
    (): DeckSaveRequest => ({
      name: name.trim(),
      starterCount,
      relieverCount,
      closerCount,
      winTactic,
      chaseTactic,
      players: allSlots.map((slot) => {
        const p = players[slot];
        return {
          slot,
          ...(p.playerName?.trim() ? { playerName: p.playerName.trim() } : {}),
          cardGrade: p.cardGrade,
          cardVariant: p.cardVariant ?? CardVariant.NONE,
          skills: p.skills,
          ...(isLineup(slot) ? { battingOrder: p.battingOrder } : {}),
          ...(isBench(slot) && p.position ? { position: p.position } : {}),
          ...(isReliever(slot) ? { relieverRole: p.relieverRole ?? RelieverRole.LONG } : {}),
          ...(p.stats && Object.keys(p.stats).length ? { stats: p.stats } : {}),
          ...(p.statsSlot ? { statsSlot: p.statsSlot } : {}),
          ...(p.throwHand ? { throwHand: p.throwHand } : {}),
          ...(p.batHand ? { batHand: p.batHand } : {}),
          ...(p.baseStats && Object.keys(p.baseStats).length ? { baseStats: p.baseStats } : {}),
          ...(p.trainingStats && Object.keys(p.trainingStats).length
            ? { trainingStats: p.trainingStats }
            : {}),
          ...(p.specialTrainingStats && Object.keys(p.specialTrainingStats).length
            ? { specialTrainingStats: p.specialTrainingStats }
            : {}),
          ...(p.transcendenceLevel != null ? { transcendenceLevel: p.transcendenceLevel } : {}),
          ...(p.enhancementLevel != null ? { enhancementLevel: p.enhancementLevel } : {}),
          ...(p.year != null ? { year: p.year } : {}),
        };
      }),
      ...(training && Object.keys(training.slots).length ? { positionTraining: training } : {}),
      ...(Object.keys(coordination).length ? { deckScoreChoices: Object.values(coordination) } : {}),
    }),
    [
      allSlots, players, name, starterCount, relieverCount, closerCount,
      winTactic, chaseTactic, training, coordination,
    ],
  );

  // 완성된 덱이면 편집하는 동안 점수를 미리 보여 준다. 저장하지 않는다.
  const previewSeq = useRef(0);
  useEffect(() => {
    const seq = ++previewSeq.current;
    if (!isComplete) {
      setScore(null);
      setScoring(false);
      return;
    }
    const run = async () => {
      setScoring(true);
      try {
        const result = await scoreDeck(toRequest());
        if (seq === previewSeq.current) setScore(result);
      } catch (err) {
        if (seq === previewSeq.current) setError(apiErrorMessage(err));
      } finally {
        if (seq === previewSeq.current) setScoring(false);
      }
    };
    void run();
    return () => { previewSeq.current += 1; };
  }, [isComplete, toRequest]);

  const save = useCallback(
    async (deckId?: number): Promise<DeckDetail | null> => {
      setSaving(true);
      setError(null);
      try {
        const payload = toRequest();
        return deckId ? await updateDeck(deckId, payload) : await createDeck(payload);
      } catch (err) {
        setError(apiErrorMessage(err));
        return null;
      } finally {
        setSaving(false);
      }
    },
    [toRequest],
  );

  /** 덱 스코어 한 칸을 고른다. 같은 칸을 다시 누르면 해제한다. */
  const chooseTier = useCallback(
    (ladder: DeckScoreLadder, threshold: number, side: DeckScoreSide, decadeYear?: number | null) => {
      const key = `${ladder}:${threshold}`;
      setCoordination((prev) => {
        const current = prev[key];
        if (current && current.side === side && (current.decadeYear ?? null) === (decadeYear ?? null)) {
          const next = { ...prev };
          delete next[key];
          return next;
        }
        return { ...prev, [key]: { ladder, threshold, side, ...(decadeYear != null ? { decadeYear } : {}) } };
      });
    },
    [],
  );

  const clearTier = useCallback((ladder: DeckScoreLadder, threshold: number) => {
    setCoordination((prev) => {
      const next = { ...prev };
      delete next[`${ladder}:${threshold}`];
      return next;
    });
  }, []);

  /**
   * 워크북에서 읽은 초안을 편집기에 붓는다.
   *
   * 워크북에는 18명뿐이라 **덮어쓰는 자리만** 손댄다. 이미 채워 둔 나머지 자리는 그대로 둔다.
   * 덱 이름은 비어 있을 때만 채운다 — 올린 사람이 이름을 이미 정했을 수 있다.
   */
  const applyImport = useCallback((draft: DeckSaveRequest, warnings: DeckImportWarning[]) => {
    setImportWarnings(warnings);
    setRoster((prev) => {
      const starterCount = draft.starterCount ?? prev.starterCount;
      const closerCount = draft.closerCount ?? prev.closerCount;
      const relieverCount = draft.relieverCount ?? (
        draft.starterCount && draft.closerCount
          ? PITCHER_COUNT - starterCount - closerCount
          : prev.relieverCount
      );
      const players = { ...prev.players };
      draft.players.forEach(({ slot, ...player }) => {
        players[slot] = { ...players[slot], ...player, slot };
      });
      return normalizeRoster({
        starterCount,
        closerCount,
        relieverCount,
        players,
        winTactic: draft.winTactic ?? prev.winTactic,
        chaseTactic: draft.chaseTactic ?? prev.chaseTactic,
      });
    });
    if (draft.deckScoreChoices?.length) {
      const seed: Record<string, DeckScoreChoice> = {};
      draft.deckScoreChoices.forEach((choice) => {
        seed[`${choice.ladder}:${choice.threshold}`] = choice;
      });
      setCoordination(seed);
    }
    setName((prev) => prev || draft.name?.trim() || '');
  }, []);

  const reload = useCallback(async (deckId: number) => {
    try {
      return await fetchDeck(deckId);
    } catch (err) {
      setError(apiErrorMessage(err));
      return null;
    }
  }, []);

  return {
    name,
    setName,
    starterCount,
    closerCount,
    relieverCount,
    pitcherCounts,
    setPitcherCount,
    pitcherTotal,
    isPitcherStaffValid,
    players,
    updatePlayer,
    moveBattingOrder,
    moveReliever,
    winTactic,
    chaseTactic,
    setWinTactic,
    setChaseTactic,
    lineupByBattingOrder,
    allSlots,
    pitcherSlots,
    completedCount,
    brokenSlots,
    skillProblems,
    isComplete,
    coordination,
    chooseTier,
    clearTier,
    importWarnings,
    applyImport,
    score,
    scoring,
    saving,
    error,
    setError,
    save,
    reload,
  };
}
