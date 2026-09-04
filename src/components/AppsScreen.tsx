import React, { useState, useMemo } from 'react';
import { ProcessApp, AppCategory, MemoryStats } from '../types';

interface AppsScreenProps {
  apps: ProcessApp[];
  memoryStats: MemoryStats;
  accessibilityEnabled: boolean;
  usageAccessEnabled: boolean;
  onOpenPermissions: () => void;
  onToggleAppSelect: (appId: string) => void;
  onSelectAll: (filteredAppIds: string[], selectAll: boolean) => void;
  onStopSelected: () => void;
  onAppInspect: (app: ProcessApp) => void;
}

export const AppsScreen: React.FC<AppsScreenProps> = ({
  apps,
  memoryStats,
  accessibilityEnabled,
  usageAccessEnabled,
  onOpenPermissions,
  onToggleAppSelect,
  onSelectAll,
  onStopSelected,
  onAppInspect,
}) => {
  const [filterCategory, setFilterCategory] = useState<AppCategory | 'all'>('user');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Counts for each category
  const allCount = apps.length;
  const userCount = apps.filter((a) => a.category === 'user').length;
  const systemCount = apps.filter((a) => a.category === 'system').length;

  // Filtered apps based on tab and search
  const filteredApps = useMemo(() => {
    return apps.filter((app) => {
      const matchesCategory =
        filterCategory === 'all' ? true : app.category === filterCategory;

      if (!matchesCategory) return false;

      if (!searchQuery.trim()) return true;

      const query = searchQuery.toLowerCase().trim();
      return (
        app.name.toLowerCase().includes(query) ||
        app.packageName.toLowerCase().includes(query) ||
        app.state.toLowerCase().includes(query)
      );
    });
  }, [apps, filterCategory, searchQuery]);

  // Selected count among filtered or overall
  const selectedFilteredApps = filteredApps.filter((a) => a.selected && a.canStop);
  const totalSelectedApps = apps.filter((a) => a.selected && a.canStop);
  const isAllFilteredSelected =
    filteredApps.length > 0 &&
    filteredApps.filter((a) => a.canStop).every((a) => a.selected);

  const handleToggleSelectAll = () => {
    const stoppableIds = filteredApps.filter((a) => a.canStop).map((a) => a.id);
    onSelectAll(stoppableIds, !isAllFilteredSelected);
  };

  return (
    <div className="flex flex-col w-full pb-24 max-w-4xl mx-auto px-4 pt-3">
      {/* System Architecture & Vitals Card */}
      <section
        aria-label="System Architecture & Vitals"
        className="w-full bg-surface-container-low rounded-2xl p-4 sm:p-5 mb-4 border border-outline/10 shadow-sm"
      >
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <span aria-hidden="true" className="w-2.5 h-2.5 rounded-full bg-primary animate-pulse" />
            <span className="font-mono text-xs font-semibold text-primary uppercase tracking-wider">
              Kernel Active Status
            </span>
          </div>
          <span className="font-mono text-[11px] text-on-surface-variant">
            API Level 34 · SELinux Enforcing
          </span>
        </div>

        {/* Authentic Metric Headline (Zero Gimmicks) */}
        <div className="flex flex-col gap-1 mb-4">
          <h1 className="text-xl sm:text-2xl font-bold text-on-surface flex items-baseline gap-2">
            <span className="text-3xl sm:text-4xl font-extrabold text-primary font-mono leading-none">
              {apps.filter((a) => a.category === 'user' && a.state !== 'stopped').length}
            </span>
            <span className="font-semibold text-lg text-on-surface">
              user apps running in background
            </span>
          </h1>
          <p className="text-xs text-on-surface-variant">
            Cached background processes evaluated via PackageManager snapshot.
          </p>
        </div>

        {/* Memory Map Values from /proc/meminfo */}
        <div className="grid grid-cols-3 gap-2 bg-surface-container-lowest rounded-xl p-3 border border-outline/10 font-mono text-center">
          <div className="flex flex-col">
            <span className="text-[10px] text-on-surface-variant tracking-tight uppercase">MemAvailable</span>
            <span className="text-xs sm:text-sm font-bold text-on-surface">{memoryStats.memAvailableMb.toLocaleString()} MiB</span>
          </div>
          <div className="flex flex-col border-x border-outline/10">
            <span className="text-[10px] text-on-surface-variant tracking-tight uppercase">Active(file)</span>
            <span className="text-xs sm:text-sm font-bold text-on-surface">{memoryStats.activeFileMb.toLocaleString()} MiB</span>
          </div>
          <div className="flex flex-col">
            <span className="text-[10px] text-on-surface-variant tracking-tight uppercase">Swap / ZRAM</span>
            <span className="text-xs sm:text-sm font-bold text-on-surface">{memoryStats.swapZramMb.toLocaleString()} MiB</span>
          </div>
        </div>
      </section>

      {/* Permission Setup Alert Banner if Accessibility is Disengaged */}
      {(!accessibilityEnabled || !usageAccessEnabled) && (
        <section
          aria-label="Permission Setup Alert"
          className="w-full bg-error-container/20 border border-error/30 rounded-2xl p-4 mb-4 shadow-sm"
        >
          <div className="flex items-start gap-3">
            <div className="w-10 h-10 rounded-xl bg-error-container text-error flex items-center justify-center shrink-0">
              <span className="material-symbols-outlined text-[24px]">warning</span>
            </div>
            <div className="flex flex-col min-w-0 flex-1">
              <span className="font-semibold text-sm text-on-surface mb-0.5">
                Setup Required: Accessibility Service Disengaged
              </span>
              <p className="text-xs text-on-surface-variant leading-relaxed mb-3">
                App Controller requires Accessibility and Usage Access to automate stopping background apps without root.
              </p>
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={onOpenPermissions}
                  className="h-11 px-4 rounded-xl bg-primary text-on-primary font-mono text-xs font-bold flex items-center gap-1.5 shadow-sm active:scale-95 transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  <span>Enable Accessibility Service</span>
                  <span className="material-symbols-outlined text-[14px]">north_east</span>
                </button>
                <span className={`h-11 px-3 rounded-xl bg-surface-container-high font-mono text-xs flex items-center gap-1.5 ${
                  usageAccessEnabled ? 'text-primary' : 'text-outline'
                }`}>
                  <span className="material-symbols-outlined text-[16px]">
                    {usageAccessEnabled ? 'check_circle' : 'pending'}
                  </span>
                  <span>Usage Scopes: {usageAccessEnabled ? 'Granted ✓' : 'Needed'}</span>
                </span>
              </div>
            </div>
          </div>
        </section>
      )}

      {/* Safe Foreground Execution Card */}
      <aside
        aria-label="Operating Safety Notice"
        className="w-full bg-surface-container rounded-2xl p-4 mb-4 border border-outline/10 shadow-sm"
      >
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-xl bg-surface-container-high flex items-center justify-center shrink-0 text-primary">
            <span className="material-symbols-outlined text-[22px]" style={{ fontVariationSettings: "'FILL' 1" }}>
              shield_with_heart
            </span>
          </div>
          <div className="flex flex-col min-w-0 flex-1">
            <div className="flex items-center gap-1.5 mb-1">
              <span className="font-semibold text-sm text-on-surface">Safe Foreground Execution</span>
              <span className="material-symbols-outlined text-[16px] text-primary" title="Verified system policy">
                check_circle
              </span>
            </div>
            <p className="text-xs text-on-surface-variant leading-relaxed">
              Android halts background tasks safely via the native <code className="text-primary font-mono font-semibold">am force-stop</code> intent guided by AccessibilityService. System daemons and critical alarms remain wholly untouched.
            </p>
          </div>
        </div>
      </aside>

      {/* Filter & Batch Selection Controls */}
      <div className="w-full flex flex-col gap-2 mb-3">
        {/* Category Tabs: All, User, System */}
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <div
            role="tablist"
            aria-label="App Categories"
            className="flex items-center gap-1 bg-surface-container-lowest p-1 rounded-xl border border-outline/10"
          >
            <button
              type="button"
              role="tab"
              aria-selected={filterCategory === 'all'}
              onClick={() => setFilterCategory('all')}
              className={`h-10 px-3.5 rounded-lg font-mono text-xs transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                filterCategory === 'all'
                  ? 'bg-surface-container text-primary font-bold shadow-sm'
                  : 'text-on-surface-variant hover:text-on-surface'
              }`}
            >
              All Apps ({allCount})
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={filterCategory === 'user'}
              onClick={() => setFilterCategory('user')}
              className={`h-10 px-3.5 rounded-lg font-mono text-xs transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                filterCategory === 'user'
                  ? 'bg-surface-container text-primary font-bold shadow-sm'
                  : 'text-on-surface-variant hover:text-on-surface'
              }`}
            >
              User Apps ({userCount})
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={filterCategory === 'system'}
              onClick={() => setFilterCategory('system')}
              className={`h-10 px-3.5 rounded-lg font-mono text-xs transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                filterCategory === 'system'
                  ? 'bg-surface-container text-primary font-bold shadow-sm'
                  : 'text-on-surface-variant hover:text-on-surface'
              }`}
            >
              System Apps ({systemCount})
            </button>
          </div>

          {/* Select All Toggle Button */}
          <button
            type="button"
            onClick={handleToggleSelectAll}
            aria-label="Toggle selection for all filtered apps"
            className="h-11 px-3.5 rounded-xl bg-surface-container hover:bg-surface-container-high text-on-surface font-mono text-xs flex items-center gap-2 border border-outline/10 active:scale-95 transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <span className="material-symbols-outlined text-[18px]">checklist</span>
            <span>{isAllFilteredSelected ? 'Deselect All' : `Select All (${filteredApps.filter((a) => a.canStop).length})`}</span>
          </button>
        </div>

        {/* Live Search Bar */}
        <div className="relative w-full h-11 bg-surface-container rounded-xl px-3 flex items-center gap-2 border border-outline/10 focus-within:border-primary/50 transition-colors">
          <span className="material-symbols-outlined text-outline text-[20px]">search</span>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search apps or package name (e.g., spotify, com.android)..."
            aria-label="Search applications or package identifiers"
            className="flex-1 bg-transparent text-on-surface font-mono text-xs placeholder:text-outline focus:outline-none"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery('')}
              aria-label="Clear search input"
              className="w-8 h-8 flex items-center justify-center text-outline hover:text-on-surface rounded-full"
            >
              <span className="material-symbols-outlined text-[16px]">cancel</span>
            </button>
          )}
        </div>

        {/* Status Line */}
        <div className="flex items-center justify-between px-1 text-xs font-mono text-on-surface-variant">
          <span aria-live="polite">
            {totalSelectedApps.length} ready to stop
          </span>
          <span>Tap row to inspect details</span>
        </div>
      </div>

      {/* Real Applications List */}
      <section aria-label="Background applications list" className="flex flex-col gap-2 w-full">
        {filteredApps.length === 0 ? (
          <div className="p-8 text-center bg-surface-container-lowest rounded-2xl border border-outline/10">
            <span className="material-symbols-outlined text-[36px] text-outline mb-2">find_in_page</span>
            <p className="text-sm font-mono text-on-surface-variant">No matching applications found</p>
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="mt-3 px-3 py-1.5 rounded-lg text-xs font-mono text-primary hover:bg-primary/10"
              >
                Clear search query
              </button>
            )}
          </div>
        ) : (
          filteredApps.map((app) => {
            const isSelected = !!app.selected;
            const canStop = app.canStop;

            return (
              <article
                key={app.id}
                className={`h-16 w-full rounded-2xl px-3.5 flex items-center justify-between gap-3 transition-all duration-150 border select-none ${
                  isSelected
                    ? 'bg-surface-container-high border-primary/40 shadow-sm'
                    : 'bg-surface-container border-outline/10 hover:bg-surface-container-high/60'
                } ${!canStop ? 'opacity-70' : ''}`}
              >
                {/* Clickable Card Body (inspects details) */}
                <div
                  role="button"
                  tabIndex={0}
                  onClick={() => onAppInspect(app)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      onAppInspect(app);
                    }
                  }}
                  aria-label={`Inspect ${app.name} details`}
                  className="flex items-center gap-3 min-w-0 flex-1 cursor-pointer focus:outline-none"
                >
                  {/* App Icon */}
                  <div className="w-10 h-10 rounded-xl bg-surface-container-lowest flex items-center justify-center shrink-0 border border-outline/10 text-primary">
                    <span className="material-symbols-outlined text-[22px]">
                      {app.iconName || 'apps'}
                    </span>
                  </div>

                  {/* App Information */}
                  <div className="flex flex-col min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-sm text-on-surface truncate">
                        {app.name}
                      </span>
                      <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-surface-container-lowest text-on-surface-variant truncate">
                        {app.stateDetail}
                      </span>
                      {app.category === 'system' && (
                        <span className="text-[9px] font-mono px-1 rounded bg-outline/20 text-outline font-bold">
                          SYSTEM
                        </span>
                      )}
                    </div>
                    <span className="text-xs font-mono text-outline truncate">
                      {app.packageName}
                    </span>
                  </div>
                </div>

                {/* Selection Checkbox with 48px Touch Target */}
                {canStop ? (
                  <label
                    className="w-12 h-12 flex items-center justify-center cursor-pointer shrink-0"
                    aria-label={`Select ${app.name} to stop`}
                  >
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => onToggleAppSelect(app.id)}
                      className="sr-only peer"
                    />
                    <div className="w-5 h-5 rounded-md bg-surface-container-lowest border border-outline/30 peer-checked:bg-primary peer-checked:border-primary flex items-center justify-center transition-all">
                      <span className="material-symbols-outlined text-[16px] text-on-primary font-bold opacity-0 peer-checked:opacity-100 transition-opacity">
                        check
                      </span>
                    </div>
                  </label>
                ) : (
                  <div className="w-12 h-12 flex items-center justify-center shrink-0 text-outline" title="Kernel Locked">
                    <span className="material-symbols-outlined text-[18px]">lock</span>
                  </div>
                )}
              </article>
            );
          })
        )}
      </section>

      {/* Sticky / Floating Execution Hero Action */}
      <div className="sticky bottom-20 w-full pt-4 z-30">
        <button
          type="button"
          disabled={totalSelectedApps.length === 0}
          onClick={onStopSelected}
          aria-label={`Stop ${totalSelectedApps.length} selected apps`}
          className={`w-full h-14 bg-primary text-on-primary rounded-2xl px-5 flex items-center justify-between shadow-2xl active:scale-[0.99] transition-all duration-150 focus:outline-none focus-visible:ring-4 focus-visible:ring-primary/40 ${
            totalSelectedApps.length === 0 ? 'opacity-40 cursor-not-allowed' : 'opacity-100'
          }`}
        >
          <div className="flex items-center gap-2 font-bold text-base">
            <span className="material-symbols-outlined text-[24px]">power_settings_new</span>
            <span>Stop selected apps</span>
          </div>
          <span className="font-mono text-xs font-bold bg-on-primary/20 px-3 py-1 rounded-full text-on-primary">
            {totalSelectedApps.length}
          </span>
        </button>
      </div>
    </div>
  );
};
