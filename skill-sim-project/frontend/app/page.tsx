'use client';

import { useMemo, useState } from 'react';
import SkillCard from '../components/SkillCard';
import { Grade, RollRequest, RollResponse, SkillSlot, TicketType } from '../types';

const ticketOptions = [
    { value: TicketType.SKILL_CHANGE, label: 'Skill Change Ticket', helper: 'Lower odds for high tiers/grades' },
    { value: TicketType.PREMIUM_SKILL_CHANGE, label: 'Premium Skill Change Ticket', helper: 'Balanced odds' },
    { value: TicketType.SUPREME_SKILL_CHANGE, label: 'Supreme Skill Change Ticket', helper: 'Gold-tier guarantee, high grade odds' },
];

export default function Page() {
    const [ticketType, setTicketType] = useState<TicketType>(TicketType.SKILL_CHANGE);
    const [useProtection, setUseProtection] = useState(false);
    const [slots, setSlots] = useState<SkillSlot[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const currentGrades = useMemo<Grade[]>(
        () => (slots.length ? slots.map((slot) => slot.grade ?? Grade.D) : [Grade.D, Grade.D, Grade.D]),
        [slots]
    );

    const handleRoll = async () => {
        const payload: RollRequest = {
            ticketType,
            useProtection,
            currentGrades,
        };

        try {
            setLoading(true);
            setError(null);
            const res = await fetch('http://localhost:8080/api/skills/roll', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            if (!res.ok) throw new Error('Request failed');
            const data: RollResponse = await res.json();
            setSlots(data.slots);
        } catch (err) {
            setError('Failed to roll skills. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <main className="min-h-screen bg-slate-50">
            <div className="mx-auto max-w-5xl px-6 py-12">
                <header className="flex flex-wrap items-center justify-between gap-4">
                    <div>
                        <p className="text-sm font-medium uppercase tracking-wide text-indigo-700">MLB Rivals</p>
                        <h1 className="text-3xl font-bold text-slate-900">Skill Change Simulator</h1>
                        <p className="text-sm text-slate-600">Roll three skill slots with ticket-based odds and optional grade protection.</p>
                    </div>
                    <button
                        onClick={handleRoll}
                        disabled={loading}
                        className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-indigo-300"
                    >
                        {loading ? 'Rolling...' : 'Roll'}
                    </button>
                </header>

                <section className="mt-8 grid gap-6 lg:grid-cols-3">
                    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm lg:col-span-1">
                        <h2 className="text-base font-semibold text-slate-900">Ticket Type</h2>
                        <div className="mt-3 space-y-3">
                            {ticketOptions.map((opt) => (
                                <label
                                    key={opt.value}
                                    className="flex cursor-pointer items-start gap-3 rounded-lg border border-transparent p-2 hover:border-indigo-200"
                                >
                                    <input
                                        type="radio"
                                        name="ticketType"
                                        value={opt.value}
                                        checked={ticketType === opt.value}
                                        onChange={() => setTicketType(opt.value)}
                                        className="mt-1 h-4 w-4 text-indigo-600"
                                    />
                                    <div>
                                        <p className="text-sm font-semibold text-slate-900">{opt.label}</p>
                                        <p className="text-xs text-slate-600">{opt.helper}</p>
                                    </div>
                                </label>
                            ))}
                        </div>

                        <div className="mt-6 flex items-center justify-between rounded-lg bg-slate-50 p-3">
                            <div>
                                <p className="text-sm font-semibold text-slate-900">Use Level Protection</p>
                                <p className="text-xs text-slate-600">Keeps grade from dropping below current per slot.</p>
                            </div>
                            <label className="relative inline-flex cursor-pointer items-center">
                                <input
                                    type="checkbox"
                                    checked={useProtection}
                                    onChange={(e) => setUseProtection(e.target.checked)}
                                    className="peer sr-only"
                                />
                                <div className="peer h-6 w-11 rounded-full bg-slate-300 transition peer-checked:bg-indigo-600 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-indigo-300">
                                    <div className="absolute left-1 top-1 h-4 w-4 rounded-full bg-white transition peer-checked:translate-x-5" />
                                </div>
                            </label>
                        </div>
                    </div>

                    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm lg:col-span-2">
                        <div className="flex items-center justify-between">
                            <h2 className="text-base font-semibold text-slate-900">Skill Slots</h2>
                            <p className="text-xs text-slate-500">Passing current grades back to backend when protection is on.</p>
                        </div>
                        {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
                        {!slots.length && !error && (
                            <p className="mt-3 text-sm text-slate-600">Press “Roll” to generate skills based on your ticket choice.</p>
                        )}
                        <div className="mt-4 grid gap-4 md:grid-cols-2">
                            {slots.map((slot, idx) => (
                                <SkillCard key={`${slot.skill.id}-${idx}`} skillSlot={slot} slotNumber={idx + 1} />
                            ))}
                        </div>
                    </div>
                </section>
            </div>
        </main>
    );
}
