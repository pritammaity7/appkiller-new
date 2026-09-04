import React from 'react';

export const SafetyScreen: React.FC = () => {
  return (
    <div className="flex flex-col w-full pb-24 max-w-4xl mx-auto px-4 pt-3 space-y-4">
      {/* Header Banner */}
      <section className="bg-surface-container-low rounded-2xl p-5 border border-outline/10">
        <div className="flex items-center gap-3 mb-2">
          <div className="w-10 h-10 rounded-xl bg-primary/20 text-primary flex items-center justify-center shrink-0">
            <span className="material-symbols-outlined text-[24px]">verified_user</span>
          </div>
          <div>
            <h2 className="font-bold text-lg text-on-surface">Kernel Safety & Policy Declarations</h2>
            <span className="font-mono text-xs text-primary font-semibold">
              Android 14 (API Level 34) Hardened
            </span>
          </div>
        </div>
        <p className="text-xs text-on-surface-variant leading-relaxed">
          App Controller adheres to strict system boundaries and Google Play platform compliance. We believe system utilities must be transparent, safe, and free from misleading marketing gimmicks.
        </p>
      </section>

      {/* Safety Directive 1: Accessibility Service Transparency */}
      <section className="bg-surface-container rounded-2xl p-4 sm:p-5 border border-outline/10 space-y-3">
        <div className="flex items-center gap-2 text-on-surface font-semibold text-sm sm:text-base">
          <span className="material-symbols-outlined text-primary text-[20px]">accessibility_new</span>
          <h3>AccessibilityService API Disclosure</h3>
        </div>
        <div className="text-xs text-on-surface-variant leading-relaxed space-y-2">
          <p>
            This application utilizes the Android <code className="font-mono text-primary">AccessibilityService API</code> solely to automate navigation to standard App Settings screens and perform user-directed "Force Stop" actions.
          </p>
          <ul className="list-disc list-inside space-y-1 text-[11px] font-mono text-on-surface-variant/90 pl-1">
            <li>No user keystrokes, messages, or screen contents are recorded or read.</li>
            <li>Zero network transmission: Operates 100% locally on your device with no remote servers.</li>
            <li>Actions only execute when explicitly initiated by the user via the "Stop selected apps" action.</li>
          </ul>
        </div>
      </section>

      {/* Safety Directive 2: Zero Fake Features Manifesto */}
      <section className="bg-surface-container rounded-2xl p-4 sm:p-5 border border-outline/10 space-y-3">
        <div className="flex items-center gap-2 text-on-surface font-semibold text-sm sm:text-base">
          <span className="material-symbols-outlined text-secondary text-[20px]">fact_check</span>
          <h3>No Fake Metrics Guarantee</h3>
        </div>
        <div className="text-xs text-on-surface-variant leading-relaxed space-y-2">
          <p>
            Standard "RAM cleaner" and "Speed booster" apps often display fabricated numbers (such as "RAM freed: 3.8 GB" or "Battery saved: 42%") to mislead users. In modern Linux and Android architecture:
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 font-mono text-[11px] pt-1">
            <div className="bg-surface-container-lowest p-3 rounded-xl border border-outline/10">
              <span className="text-primary font-bold block mb-1">Authentic /proc/meminfo</span>
              <span>Values displayed reflect true Linux memory maps: MemAvailable, Active Cache, and ZRAM.</span>
            </div>
            <div className="bg-surface-container-lowest p-3 rounded-xl border border-outline/10">
              <span className="text-secondary font-bold block mb-1">Zero Fabricated Scores</span>
              <span>No synthetic performance scores, placebo cooling meters, or fake battery percentages.</span>
            </div>
          </div>
        </div>
      </section>

      {/* Safety Directive 3: Non-Stoppable Critical Components */}
      <section className="bg-surface-container rounded-2xl p-4 sm:p-5 border border-outline/10 space-y-3">
        <div className="flex items-center gap-2 text-on-surface font-semibold text-sm sm:text-base">
          <span className="material-symbols-outlined text-primary text-[20px]">shield</span>
          <h3>Protected Baseline Guardrails</h3>
        </div>
        <p className="text-xs text-on-surface-variant leading-relaxed">
          To protect device stability and prevent interface freezes, App Controller strictly forbids automatic termination of:
        </p>
        <div className="space-y-1.5 font-mono text-xs">
          <div className="flex items-center gap-2 p-2 bg-surface-container-lowest rounded-lg border border-outline/10">
            <span className="material-symbols-outlined text-[16px] text-primary">check</span>
            <span className="text-on-surface">App Controller and its active AccessibilityService</span>
          </div>
          <div className="flex items-center gap-2 p-2 bg-surface-container-lowest rounded-lg border border-outline/10">
            <span className="material-symbols-outlined text-[16px] text-primary">check</span>
            <span className="text-on-surface">The active home screen launcher (Quickstep / Nova / OneUI)</span>
          </div>
          <div className="flex items-center gap-2 p-2 bg-surface-container-lowest rounded-lg border border-outline/10">
            <span className="material-symbols-outlined text-[16px] text-primary">check</span>
            <span className="text-on-surface">Active Input Methods (Gboard, SwiftKey, IME services)</span>
          </div>
          <div className="flex items-center gap-2 p-2 bg-surface-container-lowest rounded-lg border border-outline/10">
            <span className="material-symbols-outlined text-[16px] text-primary">check</span>
            <span className="text-on-surface">Core Android System UI and PID 1 / Zygote handlers</span>
          </div>
        </div>
      </section>

      {/* Safety Directive 4: Package Visibility Compliance */}
      <section className="bg-surface-container rounded-2xl p-4 sm:p-5 border border-outline/10 space-y-3">
        <div className="flex items-center gap-2 text-on-surface font-semibold text-sm sm:text-base">
          <span className="material-symbols-outlined text-tertiary text-[20px]">policy</span>
          <h3>Package Visibility & Query Permissions</h3>
        </div>
        <p className="text-xs text-on-surface-variant leading-relaxed">
          The <code className="font-mono text-primary">QUERY_ALL_PACKAGES</code> permission is only utilized for its legitimate core utility: enumerating installed user and system packages so you can review background processes and select which apps to halt. App Controller does not harvest, store, or profile installed applications.
        </p>
      </section>
    </div>
  );
};
