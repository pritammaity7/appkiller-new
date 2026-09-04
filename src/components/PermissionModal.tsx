import React, { useState } from 'react';

interface PermissionModalProps {
  isOpen: boolean;
  onClose: () => void;
  accessibilityEnabled: boolean;
  usageAccessEnabled: boolean;
  onToggleAccessibility: (enabled: boolean) => void;
  onToggleUsageAccess: (enabled: boolean) => void;
}

export const PermissionModal: React.FC<PermissionModalProps> = ({
  isOpen,
  onClose,
  accessibilityEnabled,
  usageAccessEnabled,
  onToggleAccessibility,
  onToggleUsageAccess,
}) => {
  const [activeStep, setActiveStep] = useState<number>(1);
  const [simulatedSettingOpen, setSimulatedSettingOpen] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSimulateLink = (type: 'accessibility' | 'usage') => {
    setSimulatedSettingOpen(type);
  };

  const handleGrant = (type: 'accessibility' | 'usage') => {
    if (type === 'accessibility') {
      onToggleAccessibility(true);
    } else {
      onToggleUsageAccess(true);
    }
    setSimulatedSettingOpen(null);
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="permission-dialog-title"
      aria-describedby="permission-dialog-desc"
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4 bg-black/75 backdrop-blur-sm animate-in fade-in duration-200"
    >
      <div className="w-full max-w-lg bg-surface-container rounded-t-2xl sm:rounded-2xl border border-outline/15 shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Modal Header */}
        <div className="p-4 sm:p-5 border-b border-outline/10 flex items-start justify-between gap-3 bg-surface-container-high">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-error-container/20 text-error flex items-center justify-center shrink-0 border border-error/20">
              <span className="material-symbols-outlined text-[24px]">security</span>
            </div>
            <div>
              <h2 id="permission-dialog-title" className="font-semibold text-lg text-on-surface leading-snug">
                Required Android Privileges
              </h2>
              <p id="permission-dialog-desc" className="text-xs text-on-surface-variant font-mono">
                System Setup · Rootless Automation
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Dismiss setup dialog"
            className="w-10 h-10 rounded-full flex items-center justify-center text-on-surface-variant hover:text-on-surface hover:bg-surface-container transition-colors"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-4 sm:p-5 overflow-y-auto space-y-4 text-sm text-on-surface">
          {simulatedSettingOpen ? (
            <div className="bg-surface-container-lowest p-4 rounded-xl border border-primary/30 space-y-3">
              <div className="flex items-center justify-between text-xs font-mono text-primary">
                <span>{simulatedSettingOpen === 'accessibility' ? 'android.settings.ACCESSIBILITY_SETTINGS' : 'android.settings.USAGE_ACCESS_SETTINGS'}</span>
                <span className="px-1.5 py-0.5 rounded bg-primary/20">System Intent</span>
              </div>
              <p className="text-sm text-on-surface leading-relaxed">
                {simulatedSettingOpen === 'accessibility'
                  ? 'Locate "App Controller" in Downloaded Services and toggle switch to ON. This allows automated stopping of background apps without root.'
                  : 'Locate "App Controller" in Usage Access and allow access to read real package states and cached memory.'}
              </p>
              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setSimulatedSettingOpen(null)}
                  className="px-3 py-2 rounded-lg text-xs font-mono text-on-surface-variant hover:text-on-surface"
                >
                  Back
                </button>
                <button
                  type="button"
                  onClick={() => handleGrant(simulatedSettingOpen as any)}
                  className="px-4 py-2 rounded-lg text-xs font-mono bg-primary text-on-primary font-bold shadow-sm"
                >
                  Simulate Granting Privilege ✓
                </button>
              </div>
            </div>
          ) : (
            <>
              <div className="bg-surface-container-low p-3.5 rounded-xl border border-outline/10 text-xs text-on-surface-variant leading-relaxed">
                <strong className="text-on-surface font-semibold block mb-1">Why are these permissions needed?</strong>
                Android restricts background process termination for third-party apps. By utilizing the official <span className="font-mono text-primary">AccessibilityService API</span>, App Controller automates opening standard app details and clicking "Force Stop" natively.
                <span className="block mt-1.5 text-[11px] text-outline">
                  Zero telemetry. All actions execute 100% locally on device.
                </span>
              </div>

              {/* Permission Item 1: Accessibility */}
              <div className="p-3.5 rounded-xl bg-surface-container-low border border-outline/10 flex items-center justify-between gap-3">
                <div className="flex items-center gap-3 min-w-0">
                  <div className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${
                    accessibilityEnabled ? 'bg-primary/20 text-primary' : 'bg-error-container/20 text-error'
                  }`}>
                    <span className="material-symbols-outlined text-[20px]">
                      {accessibilityEnabled ? 'accessibility_new' : 'accessibility'}
                    </span>
                  </div>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-sm text-on-surface">Accessibility Service</span>
                      {accessibilityEnabled && (
                        <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-primary/20 text-primary font-bold">
                          Active
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-on-surface-variant font-mono truncate">
                      Automates native force-stop intent clicks
                    </p>
                  </div>
                </div>

                <div className="shrink-0">
                  {accessibilityEnabled ? (
                    <button
                      type="button"
                      onClick={() => onToggleAccessibility(false)}
                      className="text-xs font-mono px-3 py-1.5 rounded-lg bg-surface-container-high text-on-surface-variant hover:text-on-surface"
                    >
                      Revoke
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => handleSimulateLink('accessibility')}
                      className="text-xs font-mono font-bold px-3 py-2 rounded-lg bg-primary text-on-primary hover:bg-primary-container shadow-sm flex items-center gap-1 active:scale-95 transition-transform"
                    >
                      <span>Enable</span>
                      <span className="material-symbols-outlined text-[14px]">open_in_new</span>
                    </button>
                  )}
                </div>
              </div>

              {/* Permission Item 2: Usage Access */}
              <div className="p-3.5 rounded-xl bg-surface-container-low border border-outline/10 flex items-center justify-between gap-3">
                <div className="flex items-center gap-3 min-w-0">
                  <div className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${
                    usageAccessEnabled ? 'bg-primary/20 text-primary' : 'bg-surface-container-high text-outline'
                  }`}>
                    <span className="material-symbols-outlined text-[20px]">
                      query_stats
                    </span>
                  </div>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-sm text-on-surface">Usage Data Access</span>
                      {usageAccessEnabled && (
                        <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-primary/20 text-primary font-bold">
                          Active
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-on-surface-variant font-mono truncate">
                      Reads authentic background process states
                    </p>
                  </div>
                </div>

                <div className="shrink-0">
                  {usageAccessEnabled ? (
                    <button
                      type="button"
                      onClick={() => onToggleUsageAccess(false)}
                      className="text-xs font-mono px-3 py-1.5 rounded-lg bg-surface-container-high text-on-surface-variant hover:text-on-surface"
                    >
                      Revoke
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => handleSimulateLink('usage')}
                      className="text-xs font-mono font-bold px-3 py-2 rounded-lg bg-surface-container-highest text-on-surface hover:bg-primary hover:text-on-primary shadow-sm flex items-center gap-1 active:scale-95 transition-transform"
                    >
                      <span>Enable</span>
                      <span className="material-symbols-outlined text-[14px]">open_in_new</span>
                    </button>
                  )}
                </div>
              </div>
            </>
          )}
        </div>

        {/* Modal Footer */}
        <div className="p-4 sm:p-5 border-t border-outline/10 bg-surface-container-high flex items-center justify-between">
          <div className="flex items-center gap-1 text-xs font-mono text-on-surface-variant">
            <span className="material-symbols-outlined text-[16px] text-primary">verified</span>
            <span>Local SELinux sandbox</span>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2.5 rounded-xl bg-primary text-on-primary font-bold text-xs font-mono shadow-md active:scale-95 transition-transform"
          >
            {accessibilityEnabled ? 'Continue to Controller' : 'Dismiss for now'}
          </button>
        </div>
      </div>
    </div>
  );
};
