import React, { useState, useEffect, useMemo } from 'react';
import {
  ThemeMode,
  TextScale,
  NavTab,
  ProcessApp,
  WhitelistItem,
  MemoryStats,
} from './types';
import {
  INITIAL_APPS,
  INITIAL_GUARDRAILS,
  INITIAL_USER_WHITELIST,
  INITIAL_MEMORY_STATS,
} from './data/mockApps';
import { Header } from './components/Header';
import { BottomNav } from './components/BottomNav';
import { AppsScreen } from './components/AppsScreen';
import { ExceptionsScreen } from './components/ExceptionsScreen';
import { SafetyScreen } from './components/SafetyScreen';
import { SettingsScreen } from './components/SettingsScreen';
import { PermissionModal } from './components/PermissionModal';
import { StoppingModal } from './components/StoppingModal';
import { AppDetailModal } from './components/AppDetailModal';
import { DeviceFrame } from './components/DeviceFrame';
import { AndroidExportModal } from './components/AndroidExportModal';

export default function App() {
  // Navigation
  const [activeTab, setActiveTab] = useState<NavTab>('apps');

  // Application process lists
  const [apps, setApps] = useState<ProcessApp[]>(() => {
    const saved = localStorage.getItem('app_controller_apps');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {
        // fallback
      }
    }
    return INITIAL_APPS;
  });

  // Guardrails & User Whitelist
  const [guardrails, setGuardrails] = useState<WhitelistItem[]>(INITIAL_GUARDRAILS);
  const [userWhitelist, setUserWhitelist] = useState<WhitelistItem[]>(() => {
    const saved = localStorage.getItem('app_controller_whitelist');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {
        // fallback
      }
    }
    return INITIAL_USER_WHITELIST;
  });

  // Memory Stats
  const [memoryStats, setMemoryStats] = useState<MemoryStats>(INITIAL_MEMORY_STATS);

  // Privileges & Permissions
  const [accessibilityEnabled, setAccessibilityEnabled] = useState<boolean>(() => {
    return localStorage.getItem('app_controller_a11y') === 'true';
  });
  const [usageAccessEnabled, setUsageAccessEnabled] = useState<boolean>(() => {
    return localStorage.getItem('app_controller_usage') === 'true';
  });

  // Permission onboarding modal: opens on first open if accessibility not enabled
  const [isPermissionModalOpen, setIsPermissionModalOpen] = useState<boolean>(() => {
    const hasSeen = localStorage.getItem('app_controller_onboarding_shown');
    if (!hasSeen) {
      localStorage.setItem('app_controller_onboarding_shown', 'true');
      return true;
    }
    return false;
  });

  // Modals
  const [isStoppingModalOpen, setIsStoppingModalOpen] = useState<boolean>(false);
  const [inspectedApp, setInspectedApp] = useState<ProcessApp | null>(null);
  const [isAndroidExportOpen, setIsAndroidExportOpen] = useState<boolean>(false);

  // Settings: Theme, Text scale, Motion, Contrast, Frame
  const [theme, setTheme] = useState<ThemeMode>('dark');
  const [textScale, setTextScale] = useState<TextScale>('normal');
  const [reducedMotion, setReducedMotion] = useState<boolean>(false);
  const [highContrast, setHighContrast] = useState<boolean>(false);
  const [isDeviceFrameActive, setIsDeviceFrameActive] = useState<boolean>(false);

  // Persist changes
  useEffect(() => {
    localStorage.setItem('app_controller_apps', JSON.stringify(apps));
  }, [apps]);

  useEffect(() => {
    localStorage.setItem('app_controller_whitelist', JSON.stringify(userWhitelist));
  }, [userWhitelist]);

  useEffect(() => {
    localStorage.setItem('app_controller_a11y', String(accessibilityEnabled));
  }, [accessibilityEnabled]);

  useEffect(() => {
    localStorage.setItem('app_controller_usage', String(usageAccessEnabled));
  }, [usageAccessEnabled]);

  // Compute active effective theme
  const effectiveThemeClass = useMemo(() => {
    if (theme === 'system') {
      const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
      return prefersDark ? 'theme-dark' : 'theme-light';
    }
    if (theme === 'amoled') return 'theme-amoled';
    if (theme === 'light') return 'theme-light';
    return 'theme-dark';
  }, [theme]);

  // Handle app selection toggle
  const handleToggleAppSelect = (appId: string) => {
    setApps((prev) =>
      prev.map((app) => (app.id === appId ? { ...app, selected: !app.selected } : app))
    );
  };

  // Handle batch selection
  const handleSelectAll = (filteredAppIds: string[], selectAll: boolean) => {
    const idSet = new Set(filteredAppIds);
    setApps((prev) =>
      prev.map((app) => (idSet.has(app.id) ? { ...app, selected: selectAll } : app))
    );
  };

  // Apps to stop
  const stoppableSelectedApps = useMemo(() => {
    // Exclude whitelisted packages
    const whitelistedPkgSet = new Set(userWhitelist.filter((w) => w.enabled).map((w) => w.packageName));
    return apps.filter(
      (app) => app.selected && app.canStop && !whitelistedPkgSet.has(app.packageName)
    );
  }, [apps, userWhitelist]);

  // Trigger stop sequence
  const handleStartStopSequence = () => {
    if (!accessibilityEnabled) {
      setIsPermissionModalOpen(true);
      return;
    }
    if (stoppableSelectedApps.length === 0) return;
    setIsStoppingModalOpen(true);
  };

  // Stopping sequence complete
  const handleCompleteStop = (stoppedAppIds: string[]) => {
    const stoppedSet = new Set(stoppedAppIds);
    let freedMb = 0;

    setApps((prev) =>
      prev.map((app) => {
        if (stoppedSet.has(app.id)) {
          freedMb += app.memoryMb;
          return {
            ...app,
            selected: false,
            state: 'stopped',
            stateDetail: 'Force-stopped · 0 MB active',
            pid: undefined,
          };
        }
        return app;
      })
    );

    // Update memory statistics
    setMemoryStats((prev) => ({
      ...prev,
      memAvailableMb: prev.memAvailableMb + freedMb,
      activeFileMb: Math.max(300, prev.activeFileMb - Math.round(freedMb * 0.4)),
    }));

    setIsStoppingModalOpen(false);
  };

  // Guardrail toggle
  const handleToggleGuardrail = (id: string) => {
    setGuardrails((prev) =>
      prev.map((item) => (item.id === id ? { ...item, enabled: !item.enabled } : item))
    );
  };

  // User whitelist operations
  const handleAddUserWhitelist = (packageName: string, reason: string) => {
    const existing = userWhitelist.find((w) => w.packageName === packageName);
    if (existing) return;

    // Detect friendly name if in apps list
    const foundApp = apps.find((a) => a.packageName.toLowerCase() === packageName.toLowerCase());
    const friendlyName = foundApp ? foundApp.name : packageName;

    const newItem: WhitelistItem = {
      id: `wl-${Date.now()}`,
      name: friendlyName,
      packageName,
      reason,
      enabled: true,
    };
    setUserWhitelist((prev) => [newItem, ...prev]);

    // Unselect that app if selected
    setApps((prev) =>
      prev.map((a) => (a.packageName === packageName ? { ...a, selected: false } : a))
    );
  };

  const handleRemoveUserWhitelist = (id: string) => {
    setUserWhitelist((prev) => prev.filter((item) => item.id !== id));
  };

  // Stop single app from detail modal
  const handleStopSingleApp = (app: ProcessApp) => {
    handleCompleteStop([app.id]);
  };

  // Header quick controls
  const handleHeaderSearchClick = () => {
    setActiveTab('apps');
  };

  const handleHeaderFilterClick = () => {
    setActiveTab('apps');
  };

  const isInspectedAppWhitelisted = inspectedApp
    ? userWhitelist.some((w) => w.packageName === inspectedApp.packageName)
    : false;

  return (
    <div
      className={`min-h-screen ${effectiveThemeClass} ${
        textScale === 'large'
          ? 'text-scale-large'
          : textScale === 'xlarge'
          ? 'text-scale-xlarge'
          : 'text-scale-normal'
      } ${reducedMotion ? 'reduced-motion-active' : ''} ${
        highContrast ? 'high-contrast-mode' : ''
      }`}
    >
      <DeviceFrame isActive={isDeviceFrameActive} theme={theme}>
        {/* Top App Header */}
        <Header
          activeTab={activeTab}
          onSearchClick={handleHeaderSearchClick}
          onFilterClick={handleHeaderFilterClick}
          onAvatarClick={() => setActiveTab('settings')}
          onExportAndroidClick={() => setIsAndroidExportOpen(true)}
          shizukuReady={true}
        />

        {/* Screen Content */}
        <main className="flex-1 w-full bg-surface transition-colors">
          {activeTab === 'apps' && (
            <AppsScreen
              apps={apps}
              memoryStats={memoryStats}
              accessibilityEnabled={accessibilityEnabled}
              usageAccessEnabled={usageAccessEnabled}
              onOpenPermissions={() => setIsPermissionModalOpen(true)}
              onToggleAppSelect={handleToggleAppSelect}
              onSelectAll={handleSelectAll}
              onStopSelected={handleStartStopSequence}
              onAppInspect={(app) => setInspectedApp(app)}
            />
          )}

          {activeTab === 'exceptions' && (
            <ExceptionsScreen
              guardrails={guardrails}
              userWhitelist={userWhitelist}
              onToggleGuardrail={handleToggleGuardrail}
              onAddUserWhitelist={handleAddUserWhitelist}
              onRemoveUserWhitelist={handleRemoveUserWhitelist}
            />
          )}

          {activeTab === 'safety' && <SafetyScreen />}

          {activeTab === 'settings' && (
            <SettingsScreen
              theme={theme}
              onChangeTheme={setTheme}
              textScale={textScale}
              onChangeTextScale={setTextScale}
              reducedMotion={reducedMotion}
              onToggleReducedMotion={setReducedMotion}
              highContrast={highContrast}
              onToggleHighContrast={setHighContrast}
              accessibilityEnabled={accessibilityEnabled}
              usageAccessEnabled={usageAccessEnabled}
              onOpenPermissions={() => setIsPermissionModalOpen(true)}
              isDeviceFrameActive={isDeviceFrameActive}
              onToggleDeviceFrame={() => setIsDeviceFrameActive(!isDeviceFrameActive)}
              onOpenAndroidExport={() => setIsAndroidExportOpen(true)}
            />
          )}
        </main>

        {/* Bottom Navigation */}
        <BottomNav
          activeTab={activeTab}
          onSelectTab={(tab) => setActiveTab(tab)}
          selectedCount={stoppableSelectedApps.length}
          hasPermissionAlert={!accessibilityEnabled}
        />

        {/* Permission Setup Assistant Sheet/Modal */}
        <PermissionModal
          isOpen={isPermissionModalOpen}
          onClose={() => setIsPermissionModalOpen(false)}
          accessibilityEnabled={accessibilityEnabled}
          usageAccessEnabled={usageAccessEnabled}
          onToggleAccessibility={(val) => setAccessibilityEnabled(val)}
          onToggleUsageAccess={(val) => setUsageAccessEnabled(val)}
        />

        {/* Stopping Sequence Progression Modal */}
        <StoppingModal
          isOpen={isStoppingModalOpen}
          appsToStop={stoppableSelectedApps}
          onComplete={handleCompleteStop}
          onCancel={() => setIsStoppingModalOpen(false)}
        />

        {/* App Detail Inspector Modal */}
        <AppDetailModal
          app={inspectedApp}
          onClose={() => setInspectedApp(null)}
          isWhitelisted={isInspectedAppWhitelisted}
          onToggleWhitelist={(app) => {
            if (isInspectedAppWhitelisted) {
              const item = userWhitelist.find((w) => w.packageName === app.packageName);
              if (item) handleRemoveUserWhitelist(item.id);
            } else {
              handleAddUserWhitelist(app.packageName, 'User Excluded');
            }
          }}
          onStopSingle={handleStopSingleApp}
        />

        {/* Android Native APK & Studio Project Exporter */}
        <AndroidExportModal
          isOpen={isAndroidExportOpen}
          onClose={() => setIsAndroidExportOpen(false)}
        />
      </DeviceFrame>
    </div>
  );
}
