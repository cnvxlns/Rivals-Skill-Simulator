'use client';

import SkillCard from '../components/SkillCard';
import { CardType, TicketType } from '../types';
import { useSkillSimulator } from '../lib/useSkillSimulator';

export default function Page() {
    const {
        cardType,
        setCardType,
        ticketType,
        setTicketType,
        useLevelProtection,
        setUseLevelProtection,
        slots,
        loading,
        error,
        roll,
        canLockSlot1,
        isSlot1Locked,
        toggleLockSlot1,
    } = useSkillSimulator();

    return (
        <main className="min-h-screen bg-slate-50">
            <div className="mx-auto max-w-5xl px-6 py-12">
                <header className="flex flex-wrap items-center justify-between gap-4">
                    <div>
                        <p className="text-sm font-medium uppercase tracking-wide text-indigo-700">MLB Rivals</p>
                        <h1 className="text-3xl font-bold text-slate-900">Skill Change Simulator</h1>
                        <p className="text-sm text-slate-600">Card-specific locking rules for Signature, Prime, and Moment.</p>
                    </div>
                    <button
                        onClick={roll}
                        disabled={loading}
                        className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-indigo-300"
                    >
                        {loading ? 'Rolling...' : 'Roll'}
                    </button>
                </header>

                <section className="mt-8 grid gap-6 lg:grid-cols-3">
                    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm lg:col-span-1 space-y-4">
                        <div>
                            <h2 className="text-base font-semibold text-slate-900">Card Type</h2>
                            <p className="mt-1 text-xs text-slate-600">Prime allows locking slot 1; Moment allows it only if slot 1 is a Moment tier skill.</p>
                            <select
                                value={cardType}
                                onChange={(e) => setCardType(e.target.value as CardType)}
                                className="mt-3 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                            >
                                <option value={CardType.SIGNATURE}>Signature</option>
                                <option value={CardType.PRIME}>Prime</option>
                                <option value={CardType.MOMENT}>Moment</option>
                            </select>
                        </div>

                        <div>
                            <h2 className="text-base font-semibold text-slate-900">Ticket Type</h2>
                            <select
                                value={ticketType}
                                onChange={(e) => setTicketType(e.target.value as TicketType)}
                                className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                            >
                                <option value={TicketType.SKILL_CHANGE}>Skill Change Ticket</option>
                                <option value={TicketType.PREMIUM_SKILL_CHANGE}>Premium Skill Change Ticket</option>
                                <option value={TicketType.SUPREME_SKILL_CHANGE}>Supreme Skill Change Ticket</option>
                            </select>
                        </div>

                        <label className="flex items-center justify-between rounded-lg bg-slate-50 p-3 text-sm font-medium text-slate-800">
                            <span>Use Level Protection</span>
                            <input
                                type="checkbox"
                                checked={useLevelProtection}
                                onChange={(e) => setUseLevelProtection(e.target.checked)}
                                className="h-4 w-4 text-indigo-600"
                            />
                        </label>
                    </div>

                    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm lg:col-span-2">
                        <div className="flex items-center justify-between">
                            <h2 className="text-base font-semibold text-slate-900">Skill Slots</h2>
                            <p className="text-xs text-slate-500">Slot 1 lock obeys Prime/Moment rules.</p>
                        </div>
                        {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
                        {!slots.length && !error && <p className="mt-3 text-sm text-slate-600">Press “Roll” to generate skills.</p>}
                        <div className="mt-4 grid gap-4 md:grid-cols-2">
                            {[0, 1, 2].map((idx) => {
                                const slot = slots[idx];
                                const isFirst = idx === 0;
                                return (
                                    <div key={`slot-${idx}`} className="space-y-2 rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
                                        <div className="flex items-center justify-between">
                                            <span className="text-sm font-semibold text-slate-900">Slot {idx + 1}</span>
                                            {isFirst ? (
                                                <button
                                                    type="button"
                                                    onClick={toggleLockSlot1}
                                                    disabled={!canLockSlot1}
                                                    className={`flex items-center gap-1 rounded-full px-3 py-1 text-xs font-semibold ${
                                                        isSlot1Locked
                                                            ? 'bg-green-100 text-green-800'
                                                            : 'bg-slate-100 text-slate-700'
                                                    } ${!canLockSlot1 ? 'cursor-not-allowed opacity-60' : 'hover:shadow-sm'}`}
                                                >
                                                    <span>{isSlot1Locked ? '🔒' : '🔓'}</span>
                                                    {isSlot1Locked ? 'Locked' : 'Lock slot'}
                                                </button>
                                            ) : (
                                                <span className="text-xs text-slate-400">Unlocked</span>
                                            )}
                                        </div>
                                        {slot ? (
                                            <SkillCard skillSlot={slot} slotNumber={idx + 1} />
                                        ) : (
                                            <p className="text-xs text-slate-500">No skill yet. Roll to populate.</p>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                </section>
            </div>
        </main>
    );
}
