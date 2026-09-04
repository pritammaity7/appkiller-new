import React from 'react';
import { ProcessApp } from '../types';

interface AppDetailModalProps {
  app: ProcessApp | null;
  onClose: () => void;
  onToggleWhitelist?: (app: ProcessApp) => void;
  onStopSingle?: (app: ProcessApp) => void;
  isWhitelisted?: boolean;
}

export const AppDetailModal: React.FC<AppDetailModalProps> = ({
  app,
  onClose,
  onToggleWhitelist,
  onStopSingle,
  isWhitelisted = false,
}) => {
  if (!app) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="appDetailTitle"
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4 bg-black/75 backdrop-blur-sm animate-in fade-in duration-150"
    >
      <div className="w-full max-w-md bg-surface-container rounded-t-2xl sm:rounded-2xl border border-outline/15 shadow-2xl overflow-hidden flex flex-col max-h-[85vh]">
        {/* Header */}
        <div className="p-4 bg-surface-container-high border-b border-outline/10 flex items-center justify-between">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-11 h-11 rounded-xl bg-surface-container-lowest flex items-center justify-center shrink-0 border border-outline/10 text-primary">
              <span className="material-symbols-outlined text-[24px]">
                {app.iconName || 'apps'}
              </span>
            </div>
            <div className="min-w-0">
              <h3 id="appDetailTitle" className="font-semibold text-base text-on-surface truncate">
                {app.name}
              </h3>
              <p className="font-mono text-xs text-outline truncate">
                {app.packageName}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close details"
            className="w-10 h-10 rounded-full flex items-center justify-center text-on-surface-variant hover:text-on-surface hover:bg-surface-container"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        {/* Content */}
        <div className="p-4 sm:p-5 space-y-4 overflow-y-auto text-sm text-on-surface">
          {/* Metadata Grid */}
          <div className="grid grid-cols-2 gap-2 bg-surface-container-lowest p-3 rounded-xl border border-outline/10 font-mono text-xs">
            <div>
              <span className="text-on-surface-variant block text-[11px]">CATEGORY</span>
              <span className="text-on-surface font-semibold uppercase">{app.category}</span>
            </div>
            <div>
              <span className="text-on-surface-variant block text-[11px]">PROCESS STATE</span>
              <span className="text-primary font-semibold">{app.state}</span>
            </div>
            <div>
              <span className="text-on-surface-variant block text-[11px]">MEMORY FOOTPRINT</span>
              <span className="text-on-surface">{app.memoryMb} MiB (RSS)</span>
            </div>
            <div>
              <span className="text-on-surface-variant block text-[11px]">OOM ADJ SCORE</span>
              <span className="text-on-surface">{app.oomScore ?? 'N/A'}</span>
            </div>
            <div>
              <span className="text-on-surface-variant block text-[11px]">PROCESS ID (PID)</span>
              <span className="text-on-surface">{app.pid ?? 'Cached / Zygote'}</span>
            </div>
            <div>
              <span className="text-on-surface-variant block text-[11px]">UID BOUND</span>
              <span className="text-on-surface">{app.uid ?? 'u0_a241'}</span>
            </div>
          </div>

          {/* Safety Evaluation */}
          <div className="bg-surface-container-low p-3.5 rounded-xl border border-outline/10 space-y-1">
            <div className="flex items-center gap-1.5 text-xs font-semibold text-primary">
              <span className="material-symbols-outlined text-[16px]">verified_user</span>
              <span>Kernel Safety Evaluation</span>
            </div>
            <p className="text-xs text-on-surface-variant leading-relaxed">
              {app.canStop
                ? 'Target is safe for automated stop intent. It holds no active audio sinks, input method bindings, or critical alarm triggers.'
                : 'Target is marked as a critical system process or active service. Terminating may cause interface redraw or audio cut-off.'}
            </p>
          </div>

          {/* Whitelist Toggle */}
          <div className="flex items-center justify-between p-3 bg-surface-container-low rounded-xl border border-outline/10">
            <div>
              <span className="text-xs font-medium text-on-surface block">Protected from batch stop</span>
              <span className="text-[11px] font-mono text-on-surface-variant">Add to User Whitelist</span>
            </div>
            <button
              type="button"
              onClick={() => onToggleWhitelist && onToggleWhitelist(app)}
              className={`h-9 px-3 rounded-lg text-xs font-mono font-semibold transition-colors ${
                isWhitelisted
                  ? 'bg-primary/20 text-primary border border-primary/30'
                  : 'bg-surface-container-high text-on-surface hover:bg-surface-container-highest'
              }`}
            >
              {isWhitelisted ? 'Whitelisted ✓' : '+ Add Whitelist'}
            </button>
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 bg-surface-container-high border-t border-outline/10 flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="h-11 px-4 rounded-xl text-xs font-mono text-on-surface-variant hover:text-on-surface"
          >
            Close
          </button>
          {app.canStop && (
            <button
              type="button"
              onClick={() => {
                if (onStopSingle) onStopSingle(app);
                onClose();
              }}
              className="h-11 px-4 rounded-xl bg-error-container/80 text-on-error-container font-mono text-xs font-bold hover:bg-error-container active:scale-95 transition-transform flex items-center gap-1"
            >
              <span className="material-symbols-outlined text-[16px]">power_settings_new</span>
              <span>Stop this app</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
