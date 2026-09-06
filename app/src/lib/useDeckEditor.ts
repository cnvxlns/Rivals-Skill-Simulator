'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { apiErrorMessage, createDeck, fetchDeck, scoreDeck, updateDeck } from './api';
import {
  BENCH_SLOTS,
  CardType,
  DeckDetail,
  DeckPlayer,
  DeckSaveRequest,
  DeckScoreResponse,
  LINEUP_SLOTS,
  PITCHER_COUNT,
  RelieverRole,
} from '../types';

export const STARTER_CHOICES = [4, 5, 6] as const;
export const CLOSER_CHOICES = [1, 2] as const;

/** 중계 정원은 총원 12에서 선발과 마무리를 뺀 값이다. 게임 화면의 "중간 계투(n/n)"가 이 값이다. */
export const relieverCountFor = (starters: number, closers: number) => PITCHER_COUNT - starters - closers;

export const pitcherSlotsFor = (starters: number, closers: number): string[] => [
  ...Array.from({ length: starters }, (_, i) => `SP${i + 1}`),
  ...Array.from({ length: relieverCountFor(starters, closers) }, (_, i) => `RP${i + 1}`),
  ...Array.from({ length: closers }, (_, i) => `CP${i + 1}`),
];

export const isBench = (slot: string) => (BENCH_SLOTS as readonly string[]).includes(slot);
export const isLineup = (slot: string) => (LINEUP_SLOTS as readonly string[]).includes(slot);
export const isReliever = (slot: string) => slot.startsWith('RP');
export const isPitcher = (slot: string) => /^(SP|RP|CP)\d+$/.test(slot);

/** 카드 타입별 스킬 슬롯 수. 백엔드 SkillRules.slotCount와 같은 규칙이다. */
export const slotCountFor = (cardType: string) =>
  cardType === CardType.SIGNATURE_BLACK || cardType === CardType.WBC_SIGNATURE_BLACK ? 4 : 3;

/** 스킬 목록을 조회할 때 쓰는 포지션. 후보만 직접 고르고 나머지는 자리에서 나온다. */
export const positionForSlot = (slot: string, benchPosition?: string): string => {
  if (isLineup(slot)) return slot;
  if (isPitcher(slot)) return slot.replace(/\d+$/, '');
  return benchPosition || 'C';
};

/** 아직 스킬을 다 고르지 않은 자리인가. */
export const isPlayerComplete = (player: DeckPlayer | undefined): boolean => {
  if (!player) return false;
  const needed = slotCountFor(player.cardType);
  return player.skills.length === needed && player.skills.every((s) => s.skillId);
};

const emptyPlayer = (slot: string, battingOrder?: number): DeckPlayer => ({
  slot,
  cardType: CardType.SIGNATURE,
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
  const [starterCount, setStarterCount] = useState(initial?.roster.starterCount ?? 5);
  const [closerCount, setCloserCount] = useState(initial?.roster.closerCount ?? 2);
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
    pitcherSlotsFor(5, 2).forEach((slot) => {
      seed[slot] = emptyPlayer(slot);
    });
    return seed;
  });

  const [score, setScore] = useState<DeckScoreResponse | null>(initial?.score ?? null);
  const [scoring, setScoring] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const relieverCount = relieverCountFor(starterCount, closerCount);
  const pitcherSlots = useMemo(
    () => pitcherSlotsFor(starterCount, closerCount),
    [starterCount, closerCount],
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
  const isComplete = completedCount === allSlots.length;

  const updatePlayer = useCallback((slot: string, patch: Partial<DeckPlayer>) => {
    setPlayers((prev) => ({ ...prev, [slot]: { ...prev[slot], ...patch, slot } }));
  }, []);

  const toRequest = useCallback(
    (): DeckSaveRequest => ({
      name: name.trim(),
      starterCount,
      closerCount,
      players: allSlots.map((slot) => {
        const p = players[slot];
        return {
          slot,
          cardType: p.cardType,
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
    [allSlots, players, name, starterCount, closerCount],
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
    setStarterCount,
    closerCount,
    setCloserCount,
    relieverCount,
    players,
    updatePlayer,
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
