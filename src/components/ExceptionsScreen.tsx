import React, { useState } from 'react';
import { WhitelistItem } from '../types';

interface ExceptionsScreenProps {
  guardrails: WhitelistItem[];
  userWhitelist: WhitelistItem[];
  onToggleGuardrail: (id: string) => void;
  onAddUserWhitelist: (packageName: string, reason: string) => void;
  onRemoveUserWhitelist: (id: string) => void;
}

export const ExceptionsScreen: React.FC<ExceptionsScreenProps> = ({
  guardrails,
  userWhitelist,
  onToggleGuardrail,
  onAddUserWhitelist,
  onRemoveUserWhitelist,
}) => {
  const [newPackage, setNewPackage] = useState('');
  const [newReason, setNewReason] = useState('');
  const [whitelistSearch, setWhitelistSearch] = useState('');

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newPackage.trim()) return;
    onAddUserWhitelist(newPackage.trim(), newReason.trim() || 'User Excluded');
    setNewPackage('');
    setNewReason('');
  };

  const filteredWhitelist = userWhitelist.filter(
    (item) =>
      item.name.toLowerCase().includes(whitelistSearch.toLowerCase()) ||
      item.packageName.toLowerCase().includes(whitelistSearch.toLowerCase()) ||
      item.reason.toLowerCase().includes(whitelistSearch.toLowerCase())
  );

  return (
    <div className="flex flex-col w-full pb-24 max-w-4xl mx-auto px-4 pt-3">
      {/* Kernel Guardrails Header & Description */}
      <section aria-label="Kernel Guardrails" className="w-full mb-6">
        <div className="flex items-center justify-between gap-2 mb-1.5">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-[20px] text-primary" style={{ fontVariationSettings: "'FILL' 1" }}>
              shield
            </span>
            <h2 className="font-bold text-lg text-on-surface">Kernel Guardrails</h2>
          </div>
          <span className="font-mono text-[10px] px-2 py-0.5 rounded-full bg-primary/20 text-primary font-bold tracking-wider">
            ENFORCED
          </span>
        </div>
        <p className="text-xs text-on-surface-variant leading-relaxed mb-4">
          Critical Android baseline subsystems exempted from mass-termination signals to avoid UI freeze and reboot loops.
        </p>

        {/* Guardrail List */}
        <div className="flex flex-col gap-2">
          {guardrails.map((item) => (
            <div
              key={item.id}
              className="min-h-[64px] bg-surface-container rounded-2xl px-4 py-2.5 flex items-center justify-between gap-3 border border-outline/10"
            >
              <div className="flex items-center gap-3 min-w-0 flex-1">
                <div className="w-10 h-10 rounded-xl bg-surface-container-high flex items-center justify-center shrink-0 text-primary border border-outline/10">
                  <span className="material-symbols-outlined text-[22px]">
                    {item.id === 'guard-core'
                      ? 'verified'
                      : item.id === 'guard-a11y'
                      ? 'accessibility_new'
                      : item.id === 'guard-launcher'
                      ? 'home'
                      : item.id === 'guard-ime'
                      ? 'keyboard'
                      : 'settings_ethernet'}
                  </span>
                </div>
                <div className="flex flex-col min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-sm text-on-surface truncate">
                      {item.name}
                    </span>
                    <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-surface-container-highest text-primary font-semibold">
                      {item.id === 'guard-core' ? 'Core' : item.id === 'guard-a11y' ? 'Active' : item.id === 'guard-launcher' ? 'System UI' : item.id === 'guard-ime' ? 'IME Focus' : 'PID 1 / Zygote'}
                    </span>
                  </div>
                  <span className="text-xs font-mono text-outline truncate">
                    {item.packageName}
                  </span>
                </div>
              </div>

              {/* Control State */}
              <div className="shrink-0 flex items-center justify-center w-12 h-12">
                {item.isSystemLock ? (
                  <span className="material-symbols-outlined text-outline text-[20px]" title="System Enforced">
                    lock
                  </span>
                ) : (
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      checked={item.enabled}
                      onChange={() => onToggleGuardrail(item.id)}
                      className="sr-only peer"
                      aria-label={`Toggle guardrail for ${item.name}`}
                    />
                    <div className="w-11 h-6 bg-surface-container-highest peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary" />
                  </label>
                )}
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Custom User Whitelist */}
      <section aria-label="Custom User Whitelist" className="w-full mb-6">
        <div className="flex items-center justify-between gap-2 mb-1">
          <h2 className="font-bold text-lg text-on-surface">Custom User Whitelist</h2>
          <span className="font-mono text-xs px-2 py-0.5 rounded bg-surface-container-highest text-primary font-semibold">
            {userWhitelist.length} active
          </span>
        </div>
        <p className="text-xs text-on-surface-variant leading-relaxed mb-3">
          Packages excluded from automated stop sequences
        </p>

        {/* Whitelist Search */}
        <div className="relative w-full h-11 bg-surface-container rounded-xl px-3 flex items-center gap-2 border border-outline/10 mb-3">
          <span className="material-symbols-outlined text-outline text-[18px]">search</span>
          <input
            type="text"
            value={whitelistSearch}
            onChange={(e) => setWhitelistSearch(e.target.value)}
            placeholder="Search whitelist (e.g., spotify, com.termux)..."
            aria-label="Search whitelist packages"
            className="flex-1 bg-transparent text-on-surface font-mono text-xs placeholder:text-outline focus:outline-none"
          />
        </div>

        {/* Whitelist Items List */}
        <div className="flex flex-col gap-2 mb-4">
          {filteredWhitelist.length === 0 ? (
            <div className="p-4 text-center bg-surface-container-lowest rounded-xl border border-outline/10 text-xs font-mono text-on-surface-variant">
              No packages in whitelist
            </div>
          ) : (
            filteredWhitelist.map((item) => (
              <div
                key={item.id}
                className="min-h-[64px] bg-surface-container rounded-2xl px-4 py-2.5 flex items-center justify-between gap-3 border border-outline/10"
              >
                <div className="flex items-center gap-3 min-w-0 flex-1">
                  <div className="w-10 h-10 rounded-xl bg-surface-container-high flex items-center justify-center shrink-0 text-secondary border border-outline/10">
                    <span className="material-symbols-outlined text-[22px]">
                      {item.id === 'wl-telegram' ? 'chat' : item.id === 'wl-spotify' ? 'headphones' : 'vpn_key'}
                    </span>
                  </div>
                  <div className="flex flex-col min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-sm text-on-surface truncate">
                        {item.name}
                      </span>
                      <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-surface-container-highest text-secondary font-semibold">
                        {item.reason}
                      </span>
                    </div>
                    <span className="text-xs font-mono text-outline truncate">
                      {item.packageName}
                    </span>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => onRemoveUserWhitelist(item.id)}
                  aria-label={`Remove ${item.name} from whitelist`}
                  className="w-11 h-11 flex items-center justify-center rounded-xl text-outline hover:text-error hover:bg-error-container/20 transition-colors"
                >
                  <span className="material-symbols-outlined text-[20px]">close</span>
                </button>
              </div>
            ))
          )}
        </div>

        {/* Add Package Form */}
        <form onSubmit={handleAdd} className="flex flex-col sm:flex-row items-center gap-2">
          <input
            type="text"
            value={newPackage}
            onChange={(e) => setNewPackage(e.target.value)}
            placeholder="com.example.app"
            aria-label="New package name to whitelist"
            className="w-full sm:flex-1 h-12 bg-surface-container-low border border-outline/15 rounded-xl px-4 font-mono text-xs text-on-surface placeholder:text-outline focus:outline-none focus:border-primary/50"
          />
          <button
            type="submit"
            aria-label="Add package to whitelist"
            className="w-full sm:w-auto h-12 px-5 bg-surface-container-high hover:bg-primary hover:text-on-primary text-on-surface font-mono text-xs font-bold rounded-xl border border-outline/10 flex items-center justify-center gap-2 transition-all active:scale-95 shrink-0"
          >
            <span className="material-symbols-outlined text-[18px]">add</span>
            <span>Add Package</span>
          </button>
        </form>
      </section>

      {/* Lifecycle Integrity Principles */}
      <section
        aria-label="Lifecycle Integrity Principles"
        className="w-full bg-surface-container-low rounded-2xl p-4 sm:p-5 border border-outline/10 shadow-sm"
      >
        <div className="flex items-center gap-2 mb-2">
          <span className="material-symbols-outlined text-[20px] text-primary">info</span>
          <h3 className="font-bold text-sm sm:text-base text-on-surface">Lifecycle Integrity Principles</h3>
        </div>
        <p className="text-xs text-on-surface-variant leading-relaxed mb-4">
          Android manages memory strictly via Linux OOM (Out Of Memory) adj score scoring. Force-stopping high-frequency system brokers triggers instantaneous restart overhead, spiking CPU usage rather than preserving battery.
        </p>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-4">
          <div className="p-3 bg-surface-container-lowest rounded-xl border border-outline/10">
            <span className="text-[10px] font-mono text-primary uppercase font-bold block mb-1">
              LMK PRIORITY
            </span>
            <span className="font-mono text-xs text-on-surface font-semibold block mb-0.5">
              Native ≥ 0 adj
            </span>
            <span className="text-xs text-on-surface-variant">Never killed by system</span>
          </div>

          <div className="p-3 bg-surface-container-lowest rounded-xl border border-outline/10">
            <span className="text-[10px] font-mono text-primary uppercase font-bold block mb-1">
              CACHED STATE
            </span>
            <span className="font-mono text-xs text-on-surface font-semibold block mb-0.5">
              Cached ≥ 900 adj
            </span>
            <span className="text-xs text-on-surface-variant">Safe zero-wake targets</span>
          </div>
        </div>

        <p className="text-[11px] font-mono text-outline leading-normal">
          App Controller intentionally skips active audio sinks, ongoing sync providers, and device admin handlers to prevent state corruption.
        </p>
      </section>
    </div>
  );
};
