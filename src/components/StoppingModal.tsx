import React, { useEffect, useState, useRef } from 'react';
import { ProcessApp } from '../types';

interface StoppingModalProps {
  isOpen: boolean;
  appsToStop: ProcessApp[];
  onComplete: (stoppedAppIds: string[]) => void;
  onCancel: () => void;
}

export const StoppingModal: React.FC<StoppingModalProps> = ({
  isOpen,
  appsToStop,
  onComplete,
  onCancel,
}) => {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isDone, setIsDone] = useState(false);
  const [logs, setLogs] = useState<string[]>([]);
  const [showLogs, setShowLogs] = useState(false);
  const timerRef = useRef<any>(null);

  const total = appsToStop.length;
  const currentApp = appsToStop[currentIndex];
  const progressPercent = total > 0 ? Math.round(((currentIndex) / total) * 100) : 0;

  useEffect(() => {
    if (!isOpen || total === 0) {
      setCurrentIndex(0);
      setIsDone(false);
      setLogs([]);
      return;
    }

    // Start simulation loop
    setCurrentIndex(0);
    setIsDone(false);
    setLogs([`[init] Starting automated cleanup sequence for ${total} target(s)...`]);

    const step = () => {
      setCurrentIndex((prev) => {
        if (prev >= total) {
          setIsDone(true);
          return prev;
        }

        const app = appsToStop[prev];
        if (app) {
          setLogs((l) => [
            ...l,
            `[exec] am force-stop ${app.packageName} (uid=${app.uid || '10xxx'}, pid=${app.pid || 'cached'})`,
            `[status] ${app.name} halted successfully.`,
          ]);
        }

        const nextIndex = prev + 1;
        if (nextIndex >= total) {
          setIsDone(true);
          setTimeout(() => {
            onComplete(appsToStop.map((a) => a.id));
          }, 1200);
          return total;
        }
        return nextIndex;
      });
    };

    timerRef.current = setInterval(step, 900);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isOpen, appsToStop, total]);

  const handleSkip = () => {
    if (currentIndex < total - 1) {
      const skipped = appsToStop[currentIndex];
      setLogs((l) => [...l, `[skip] User manually skipped ${skipped?.name || 'app'}`]);
      setCurrentIndex((prev) => prev + 1);
    } else {
      setIsDone(true);
      onComplete(appsToStop.slice(0, currentIndex).map((a) => a.id));
    }
  };

  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="stoppingModalHeading"
      aria-describedby="stoppingModalSubheading"
      className="fixed inset-x-4 bottom-20 z-50 max-w-lg mx-auto bg-surface-container-high rounded-2xl p-5 shadow-2xl border border-outline/15 animate-in slide-in-from-bottom-6 duration-200"
    >
      <div className="flex items-center justify-between gap-3 mb-3">
        <div className="flex items-center gap-3 min-w-0">
          <span
            className={`material-symbols-outlined text-[24px] text-primary ${
              isDone ? '' : 'animate-spin'
            }`}
          >
            {isDone ? 'task_alt' : 'sync'}
          </span>
          <div className="flex flex-col min-w-0">
            <h2 id="stoppingModalHeading" className="font-semibold text-base text-on-surface leading-tight">
              {isDone ? 'Finished safely' : 'Stopping apps…'}
            </h2>
            <span
              id="stoppingModalSubheading"
              className="text-xs text-on-surface-variant font-mono truncate"
            >
              {isDone
                ? 'All selected processes halted'
                : currentApp
                ? `Processing ${currentApp.name} (${currentApp.packageName})`
                : 'Finalizing process tables...'}
            </span>
          </div>
        </div>
        <span className="text-xs font-mono font-semibold text-primary shrink-0">
          {Math.min(currentIndex, total)} of {total}
        </span>
      </div>

      {/* Accessible Progress Bar Track */}
      <div
        role="progressbar"
        aria-valuenow={isDone ? 100 : progressPercent}
        aria-valuemin={0}
        aria-valuemax={100}
        className="w-full bg-surface-container-lowest h-2.5 rounded-full overflow-hidden mb-4"
      >
        <div
          className="bg-primary h-full transition-all duration-300 rounded-full"
          style={{ width: `${isDone ? 100 : progressPercent}%` }}
        />
      </div>

      {/* Optional Terminal Activity Stream */}
      {showLogs && (
        <div className="mb-4 bg-surface-container-lowest p-3 rounded-xl border border-outline/10 max-h-36 overflow-y-auto font-mono text-[11px] text-on-surface-variant space-y-1">
          {logs.map((log, i) => (
            <div key={i} className="leading-snug truncate">
              {log}
            </div>
          ))}
        </div>
      )}

      {/* Modal Actions */}
      <div className="flex items-center justify-between gap-2">
        <button
          type="button"
          onClick={() => setShowLogs(!showLogs)}
          className="h-10 px-3 rounded-xl text-xs font-mono text-outline hover:text-on-surface flex items-center gap-1 transition-colors"
        >
          <span className="material-symbols-outlined text-[16px]">terminal</span>
          <span>{showLogs ? 'Hide Log' : 'Inspect'}</span>
        </button>

        <div className="flex items-center gap-2">
          {!isDone && (
            <>
              <button
                type="button"
                onClick={handleSkip}
                className="h-11 px-4 rounded-xl bg-surface-container text-on-surface font-mono text-xs hover:bg-surface-container-highest transition-colors active:scale-95"
              >
                Skip this app
              </button>
              <button
                type="button"
                onClick={onCancel}
                className="h-11 px-4 rounded-xl bg-error-container/80 text-on-error-container font-mono text-xs hover:bg-error-container transition-colors active:scale-95"
              >
                Cancel
              </button>
            </>
          )}
          {isDone && (
            <button
              type="button"
              onClick={() => onComplete(appsToStop.map((a) => a.id))}
              className="h-11 px-5 rounded-xl bg-primary text-on-primary font-mono text-xs font-bold transition-all active:scale-95"
            >
              Done
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
