// 프론트엔드에서 스킬 롤 로직, 잠금 규칙, 상태 관리를 담당하는 커스텀 훅
import { useEffect, useMemo, useState } from 'react';
import { CardType, Grade, Position, RollRequest, SkillSlot, SubPosition, TicketType, Tier } from '../types';
import api, { rollSkills } from './api';

const BASE_SLOT_COUNT = 3;
const slotCountForCard = (cardType: CardType) => (cardType === CardType.SIGNATURE_BLACK ? 4 : BASE_SLOT_COUNT);

export function useSkillSimulator() {
  const [cardType, setCardType] = useState<CardType>(CardType.SIGNATURE);
  const [ticketType, setTicketType] = useState<TicketType>(TicketType.SKILL_CHANGE);
  const [useLevelProtectionSlots, setUseLevelProtectionSlots] = useState<boolean[]>(Array(slotCountForCard(CardType.SIGNATURE)).fill(false));
  const [slots, setSlots] = useState<SkillSlot[]>([]);
  const [lockSlot1, setLockSlot1] = useState<boolean>(false);
  const [position, setPosition] = useState<Position | null>(Position.PITCHER);
  const [subPosition, setSubPosition] = useState<SubPosition>('ALL');
  const [availableThemes, setAvailableThemes] = useState<string[]>([]);
  const [selectedTheme, setSelectedTheme] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [candidateSkills, setCandidateSkills] = useState<SkillSlot[] | null>(null);
  const [isSelectionModalOpen, setIsSelectionModalOpen] = useState<boolean>(false);
  const [ticketUsageCounts, setTicketUsageCounts] = useState<Record<TicketType, number>>({
    [TicketType.SKILL_CHANGE]: 0,
    [TicketType.PREMIUM_SKILL_CHANGE]: 0,
    [TicketType.SUPREME_SKILL_CHANGE]: 0,
  });
  const [protectionUsageCount, setProtectionUsageCount] = useState<number>(0);
  const ticketUsageCount = ticketUsageCounts[ticketType] ?? 0;

  const slotCount = useMemo(() => slotCountForCard(cardType), [cardType]);

  const isSlot1Moment = slots[0]?.skill?.tier === Tier.MOMENT;
  const canLockSlot1 = cardType === CardType.PRIME || (cardType === CardType.MOMENT && isSlot1Moment);
  const lockedSlots = useMemo<number[]>(() => {
    return lockSlot1 && canLockSlot1 ? [0] : [];
  }, [lockSlot1, canLockSlot1]);

  const currentGrades = useMemo<Grade[]>(() => {
    const filled = Array(slotCount).fill(Grade.D);
    slots.forEach((slot, idx) => {
      if (idx < slotCount && slot) {
        filled[idx] = slot.grade;
      }
    });
    return filled;
  }, [slots, slotCount]);

  const currentSkillIds = useMemo<(number | null)[]>(() => {
    const filled = Array(slotCount).fill(null);
    slots.forEach((slot, idx) => {
      if (idx < slotCount && slot) {
        filled[idx] = slot.skill?.id ?? null;
      }
    });
    return filled;
  }, [slots, slotCount]);

  const roll = async () => {
    const normalizedProtection = Array.from({ length: slotCount }, (_, idx) => useLevelProtectionSlots[idx] ?? false);
    const payload: RollRequest = {
      cardType,
      ticketType,
      useLevelProtectionSlots: normalizedProtection,
      lockedSlots,
      currentGrades,
      currentSkillIds,
      selectedTheme: cardType === CardType.MOMENT ? selectedTheme : null,
      position: position ?? Position.PITCHER,
      subPosition: subPosition === 'ALL' ? null : subPosition,
    };

    try {
      if (cardType === CardType.MOMENT && !selectedTheme) {
        setError('Select a Moment theme before rolling.');
        return;
      }
      setLoading(true);
      setError(null);
      const res = await rollSkills(payload);
      const rolledSlots = res.slots;
      const shouldDeferSelection =
        ticketType === TicketType.PREMIUM_SKILL_CHANGE || ticketType === TicketType.SUPREME_SKILL_CHANGE;

      if (shouldDeferSelection) {
        setCandidateSkills(rolledSlots);
        setIsSelectionModalOpen(true);
      } else {
        setSlots(rolledSlots);
      }

      setTicketUsageCounts((prev) => ({
        ...prev,
        [ticketType]: prev[ticketType] + 1,
      }));
      setProtectionUsageCount((prev) => prev + normalizedProtection.filter(Boolean).length);
    } catch (err) {
      setError('Failed to roll skills. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const toggleLockSlot1 = () => {
    if (!canLockSlot1) return;
    setLockSlot1((prev) => !prev);
  };

  const toggleLevelProtection = (slotIndex: number) => {
    if (slotIndex < 0 || slotIndex >= slotCount) return;
    setUseLevelProtectionSlots((prev) =>
      Array(slotCount)
        .fill(false)
        .map((_, idx) => {
          if (idx === slotIndex) {
            return !(prev[idx] ?? false);
          }
          return prev[idx] ?? false;
        }),
    );
  };

  const resetUsageCounts = () => {
    setTicketUsageCounts({
      [TicketType.SKILL_CHANGE]: 0,
      [TicketType.PREMIUM_SKILL_CHANGE]: 0,
      [TicketType.SUPREME_SKILL_CHANGE]: 0,
    });
    setProtectionUsageCount(0);
  };

  useEffect(() => {
    if (position) {
      setError(null);
    }
  }, [position, subPosition]);

  useEffect(() => {
    setSubPosition('ALL');
  }, [position]);

  useEffect(() => {
    // Reset state when switching card types to avoid stale slots/locks carrying over
    setSlots([]);
    setUseLevelProtectionSlots(Array(slotCount).fill(false));
    setLockSlot1(false);
    setError(null);
    setCandidateSkills(null);
    setIsSelectionModalOpen(false);
  }, [cardType, slotCount]);

  useEffect(() => {
    if (cardType !== CardType.MOMENT) {
      setAvailableThemes([]);
      setSelectedTheme(null);
      return;
    }

    const loadThemes = async () => {
      try {
        // 1. 현재 선택된 포지션 가져오기 (없으면 기본값 PITCHER)
        const rawPosition = position ?? Position.PITCHER;

        // 2. 안전하게 문자열로 바꾸고 대문자로 변환
        // Enum이 숫자든 문자든 상관없이 "PITCHER"로 만들어버림
        const positionParam = String(rawPosition).toUpperCase();
        const subPositionParam = subPosition === 'ALL' ? undefined : String(subPosition).toUpperCase();

        const res = await api.get<string[]>('/api/skills/themes', {
          params: {
            position: positionParam,
            subPosition: subPositionParam,
          },
        });

        setAvailableThemes(res.data);
        setSelectedTheme((prev) => {
          if (prev && res.data.includes(prev)) return prev;
          return res.data[0] ?? null;
        });
      } catch {
        setError('Moment themes could not be loaded.');
        setAvailableThemes([]);
        setSelectedTheme(null);
      }
    };

    loadThemes();
  }, [cardType, position, subPosition]);

  return {
    cardType,
    setCardType,
    ticketType,
    setTicketType,
    slotCount,
    position,
    setPosition,
    subPosition,
    setSubPosition,
    useLevelProtectionSlots,
    toggleLevelProtection,
    availableThemes,
    selectedTheme,
    setSelectedTheme,
    slots,
    loading,
    error,
    roll,
    canLockSlot1,
    isSlot1Locked: lockSlot1 && canLockSlot1,
    toggleLockSlot1,
    ticketUsageCount,
    ticketUsageCounts,
    protectionUsageCount,
    resetUsageCounts,
    candidateSkills,
    isSelectionModalOpen,
    applyCandidateSkills: () => {
      if (candidateSkills) {
        setSlots(candidateSkills);
      }
      setCandidateSkills(null);
      setIsSelectionModalOpen(false);
    },
    dismissCandidateSkills: () => {
      setCandidateSkills(null);
      setIsSelectionModalOpen(false);
    },
  };
}
