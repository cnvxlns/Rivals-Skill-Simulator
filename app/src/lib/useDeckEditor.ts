'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { apiErrorMessage, createDeck, fetchDeck, scoreDeck, updateDeck } from './api';
import {
  BENCH_SLOTS,
  CardGrade,
  CardVariant,
  DeckDetail,
  DeckPlayer,
  DeckSaveRequest,
  DeckScoreResponse,
  LINEUP_SLOTS,
  PITCHER_COUNT,
  RelieverRole,
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

export const pitcherSlotsFor = (starters: number, relievers: number, closers: number): string[] => [
  ...Array.from({ length: starters }, (_, i) => `SP${i + 1}`),
  ...Array.from({ length: relievers }, (_, i) => `RP${i + 1}`),
  ...Array.from({ length: closers }, (_, i) => `CP${i + 1}`),
];

export const isBench = (slot: string) => (BENCH_SLOTS as readonly string[]).includes(slot);
export const isLineup = (slot: string) => (LINEUP_SLOTS as readonly string[]).includes(slot);
export const isReliever = (slot: string) => slot.startsWith('RP');
export const isPitcher = (slot: string) => /^(SP|RP|CP)\d+$/.test(slot);

/** 스킬 슬롯 수. 등급만 본다(백엔드 CardRules.slotCount와 같은 규칙). */
export const slotCountFor = (cardGrade: string) =>
  cardGrade === CardGrade.SIGNATURE_BLACK ? 4 : 3;

/** 스킬 목록을 조회할 때 쓰는 포지션. 후보만 직접 고르고 나머지는 자리에서 나온다. */
export const positionForSlot = (slot: string, benchPosition?: string): string => {
  if (isLineup(slot)) return slot;
  if (isPitcher(slot)) return slot.replace(/\d+$/, '');
  return benchPosition || 'C';
};

/** 아직 스킬을 다 고르지 않은 자리인가. */
export const isPlayerComplete = (player: DeckPlayer | undefined): boolean => {
  if (!player) return false;
  const needed = slotCountFor(player.cardGrade);
  return player.skills.length === needed && player.skills.every((s) => s.skillId);
};

const emptyPlayer = (slot: string, battingOrder?: number): DeckPlayer => ({
  slot,
  cardGrade: CardGrade.SIGNATURE,
  cardVariant: CardVariant.NONE,
  skills: [],
  ...(battingOrder ? { battingOrder } : {}),
  ...(isBench(slot) ? { position: 'C' } : {}),
  ...(isReliever(slot) ? { relieverRole: RelieverRole.LONG } : {}),
});

/**
 * 덱 편집 상태.
 *
 * 자리(slot)를 키로 하는 맵으로 들고 있다가 저장할 때 배열로 편다. 선발·마무리 수를
 * 바꾸면 투수 자리 이름이 통째로 달라지는데, 맵이면 남는 자리만 버리고 이미 채운 자리는
 * 그대로 살릴 수 있다.
 */
export function useDeckEditor(initial?: DeckDetail) {
  const [name, setName] = useState(initial?.name ?? '');
  // 셋을 각자 독립된 상태로 둔다. 서로 강제하지 않고 합이 맞는지만 화면이 알려 준다.
  const [starterCount, setStarters] = useState(initial?.roster.starterCount ?? 5);
  const [closerCount, setClosers] = useState(initial?.roster.closerCount ?? 2);
  const [relieverCount, setRelievers] = useState(
    initial ? PITCHER_COUNT - initial.roster.starterCount - initial.roster.closerCount : 5,
  );
  const [players, setPlayers] = useState<Record<string, DeckPlayer>>(() => {
    const seed: Record<string, DeckPlayer> = {};
    if (initial) {
      initial.roster.players.forEach((p) => {
        seed[p.slot] = p;
      });
      return seed;
    }
    LINEUP_SLOTS.forEach((slot, i) => {
      seed[slot] = emptyPlayer(slot, i + 1);
    });
    BENCH_SLOTS.forEach((slot) => {
      seed[slot] = emptyPlayer(slot);
    });
    pitcherSlotsFor(5, 5, 2).forEach((slot) => {
      seed[slot] = emptyPlayer(slot);
    });
    return seed;
  });

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
    if (field === 'starters') setStarters(value);
    else if (field === 'relievers') setRelievers(value);
    else setClosers(value);
  }, []);
  const pitcherSlots = useMemo(
    () => pitcherSlotsFor(starterCount, relieverCount, closerCount),
    [starterCount, relieverCount, closerCount],
  );
  const allSlots = useMemo(
    () => [...LINEUP_SLOTS, ...BENCH_SLOTS, ...pitcherSlots],
    [pitcherSlots],
  );

  // 선발·마무리를 바꾸면 없어진 투수 자리를 버리고 새로 생긴 자리를 채운다.
  useEffect(() => {
    setPlayers((prev) => {
      const next: Record<string, DeckPlayer> = {};
      [...LINEUP_SLOTS, ...BENCH_SLOTS].forEach((slot, i) => {
        next[slot] = prev[slot] ?? emptyPlayer(slot, i < LINEUP_SLOTS.length ? i + 1 : undefined);
      });
      pitcherSlots.forEach((slot) => {
        next[slot] = prev[slot] ?? emptyPlayer(slot);
      });
      return next;
    });
  }, [pitcherSlots]);

  const completedCount = allSlots.filter((slot) => isPlayerComplete(players[slot])).length;
  // 합이 12가 아니면 자리 수 자체가 26이 아니므로 저장도 채점도 할 수 없다.
  const isComplete = completedCount === allSlots.length && isPitcherStaffValid;

  const updatePlayer = useCallback((slot: string, patch: Partial<DeckPlayer>) => {
    setPlayers((prev) => ({ ...prev, [slot]: { ...prev[slot], ...patch, slot } }));
  }, []);

  /**
   * 타순을 바꾼다. 이미 그 번호를 쓰던 자리와 맞바꾼다.
   *
   * 주전 9명이 1~9를 한 번씩 써야 하므로, 그냥 덮어쓰면 중복이 생겨 저장이 거부된다.
   * 맞바꾸면 중간에 잘못된 상태가 생기지 않는다.
   */
  const setBattingOrder = useCallback((slot: string, order: number) => {
    setPlayers((prev) => {
      const current = prev[slot]?.battingOrder;
      if (!current || current === order) return prev;
      const holder = Object.keys(prev).find(
        (other) => other !== slot && isLineup(other) && prev[other]?.battingOrder === order,
      );
      const next = { ...prev, [slot]: { ...prev[slot], battingOrder: order } };
      if (holder) next[holder] = { ...prev[holder], battingOrder: current };
      return next;
    });
  }, []);

  /** 그 타순을 지금 쓰고 있는 자리. 맞바꿀 상대를 화면에 보여 주는 데 쓴다. */
  const slotForBattingOrder = useCallback(
    (order: number) =>
      Object.keys(players).find((slot) => isLineup(slot) && players[slot]?.battingOrder === order),
    [players],
  );

  const toRequest = useCallback(
    (): DeckSaveRequest => ({
      name: name.trim(),
      starterCount,
      relieverCount,
      closerCount,
      players: allSlots.map((slot) => {
        const p = players[slot];
        return {
          slot,
          ...(p.playerName?.trim() ? { playerName: p.playerName.trim() } : {}),
          cardGrade: p.cardGrade,
          cardVariant: p.cardVariant ?? CardVariant.NONE,
          skills: p.skills,
          ...(isLineup(slot) ? { battingOrder: p.battingOrder } : {}),
          ...(isBench(slot) ? { position: p.position || 'C' } : {}),
          ...(isReliever(slot) ? { relieverRole: p.relieverRole ?? RelieverRole.LONG } : {}),
          ...(p.stats && Object.keys(p.stats).length ? { stats: p.stats } : {}),
          ...(p.throwHand ? { throwHand: p.throwHand } : {}),
          ...(p.batHand ? { batHand: p.batHand } : {}),
        };
      }),
    }),
    [allSlots, players, name, starterCount, relieverCount, closerCount],
  );

  // 완성된 덱이면 편집하는 동안 점수를 미리 보여 준다. 저장하지 않는다.
  const previewSeq = useRef(0);
  useEffect(() => {
    if (!isComplete) {
      setScore(null);
      return;
    }
    const seq = ++previewSeq.current;
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
    setBattingOrder,
    slotForBattingOrder,
    allSlots,
    pitcherSlots,
    completedCount,
    isComplete,
    score,
    scoring,
    saving,
    error,
    setError,
    save,
    reload,
  };
}
