import React, { useState } from 'react';
import { ThemeMode, TextScale } from '../types';
import { AppLogo, LAUNCHER_ASSET_TEMPLATES } from './AppLogo';

interface SettingsScreenProps {
  theme: ThemeMode;
  onChangeTheme: (theme: ThemeMode) => void;
  textScale: TextScale;
  onChangeTextScale: (scale: TextScale) => void;
  reducedMotion: boolean;
  onToggleReducedMotion: (enabled: boolean) => void;
  highContrast: boolean;
  onToggleHighContrast: (enabled: boolean) => void;
  accessibilityEnabled: boolean;
  usageAccessEnabled: boolean;
  onOpenPermissions: () => void;
  isDeviceFrameActive: boolean;
  onToggleDeviceFrame: () => void;
  onOpenAndroidExport?: () => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({
  theme,
  onChangeTheme,
  textScale,
  onChangeTextScale,
  reducedMotion,
  onToggleReducedMotion,
  highContrast,
  onToggleHighContrast,
  accessibilityEnabled,
  usageAccessEnabled,
  onOpenPermissions,
  isDeviceFrameActive,
  onToggleDeviceFrame,
  onOpenAndroidExport,
}) => {
  const [activeAssetTab, setActiveAssetTab] = useState<
    'foreground' | 'background' | 'monochrome' | 'svg'
  >('foreground');
  const [copiedAsset, setCopiedAsset] = useState<string | null>(null);

  const getAssetCode = () => {
    switch (activeAssetTab) {
      case 'foreground':
        return LAUNCHER_ASSET_TEMPLATES.ic_launcher_foreground;
      case 'background':
        return LAUNCHER_ASSET_TEMPLATES.ic_launcher_background;
      case 'monochrome':
        return LAUNCHER_ASSET_TEMPLATES.ic_launcher_monochrome;
      case 'svg':
        return `<svg width="120" height="120" viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
  <!-- Outer Shield - Security & Guardrails -->
  <path d="M60 22L30 35V62C30 83.5 43.5 98.5 60 103.5C76.5 98.5 90 83.5 90 62V35L60 22Z" fill="#003824" stroke="#4edea3" stroke-width="5.5" stroke-linejoin="round"/>
  <!-- Middle Octagonal Stop Node -->
  <path d="M48 43H72L80 51V69L72 77H48L40 69V51L48 43Z" fill="#10b981" stroke="#10b981" stroke-width="4.5" stroke-linejoin="round"/>
  <!-- Center Control Core -->
  <rect x="50" y="56" width="20" height="8" rx="4" fill="#101417"/>
</svg>`;
      default:
        return '';
    }
  };

  const handleCopyAsset = () => {
    const code = getAssetCode();
    navigator.clipboard?.writeText(code);
    setCopiedAsset(activeAssetTab);
    setTimeout(() => setCopiedAsset(null), 2000);
  };

  return (
    <div className="flex flex-col w-full pb-24 max-w-4xl mx-auto px-4 pt-3 space-y-5">
      {/* App Branding & Build Card */}
      <section className="bg-surface-container-low rounded-2xl p-5 border border-outline/10 flex items-center justify-between gap-4">
        <div className="flex items-center gap-4 min-w-0">
          <div className="w-14 h-14 rounded-2xl bg-surface-container-high border border-outline/15 flex items-center justify-center shrink-0 shadow-sm">
            <AppLogo size={36} variant="badge" />
          </div>
          <div className="min-w-0">
            <h2 className="font-bold text-lg text-on-surface truncate">App Controller</h2>
            <p className="text-xs text-on-surface-variant font-mono truncate">
              v2.4 (SDK 34) · AOSP Enforced
            </p>
            <div className="flex items-center gap-2 mt-1">
              <span className="inline-flex items-center gap-1 text-[10px] font-mono px-2 py-0.5 rounded-full bg-primary/15 text-primary font-bold">
                <span className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
                SHIZUKU BINDER READY
              </span>
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={onToggleDeviceFrame}
          aria-label="Toggle Android device frame preview"
          className="h-11 px-3.5 rounded-xl bg-surface-container hover:bg-surface-container-high text-on-surface font-mono text-xs flex items-center gap-1.5 border border-outline/10 transition-colors"
        >
          <span className="material-symbols-outlined text-[18px]">
            {isDeviceFrameActive ? 'fullscreen' : 'smartphone'}
          </span>
          <span className="hidden sm:inline">
            {isDeviceFrameActive ? 'Full Screen' : 'Phone Frame'}
          </span>
        </button>
      </section>

      {/* Native Android APK & Project Export Card */}
      <section aria-label="Native Android APK" className="bg-primary/10 rounded-2xl p-4 sm:p-5 border border-primary/25 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-primary/20 text-primary flex items-center justify-center">
              <span className="material-symbols-outlined text-[22px]">android</span>
            </div>
            <div>
              <h3 className="font-bold text-sm sm:text-base text-on-surface">Native Android APK & Source Package</h3>
              <p className="text-[11px] font-mono text-primary">Fully Functional Kotlin APK</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onOpenAndroidExport}
            className="h-9 px-3.5 rounded-xl bg-primary text-on-primary font-bold font-mono text-xs flex items-center gap-1.5 shadow-sm hover:opacity-90 active:scale-95 transition-all"
          >
            <span className="material-symbols-outlined text-[16px]">file_download</span>
            <span>Get APK & Code</span>
          </button>
        </div>
        <p className="text-xs text-on-surface-variant leading-relaxed">
          Contains the native Android Studio project with full <strong className="text-on-surface">AccessibilityService</strong>, <strong className="text-on-surface">Shizuku Binder</strong>, Linux <code className="font-mono text-primary">/proc/meminfo</code> parser, and automated GitHub Actions APK builder.
        </p>
      </section>

      {/* Theme Settings (System, Light, Dark, AMOLED) */}
      <section aria-label="Appearance & Theme" className="bg-surface-container rounded-2xl p-4 sm:p-5 border border-outline/10 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-on-surface font-semibold text-sm sm:text-base">
            <span className="material-symbols-outlined text-primary text-[20px]">palette</span>
            <h3>Display Theme</h3>
          </div>
          <span className="text-xs font-mono text-primary font-semibold uppercase">{theme}</span>
        </div>
        <p className="text-xs text-on-surface-variant">
          Select between system-linked, refined light neutral, high-contrast dark, or pure power-saving AMOLED black.
        </p>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
          {(['system', 'light', 'dark', 'amoled'] as ThemeMode[]).map((mode) => (
            <button
              key={mode}
              type="button"
              onClick={() => onChangeTheme(mode)}
              aria-pressed={theme === mode}
              className={`h-12 rounded-xl font-mono text-xs capitalize flex items-center justify-center gap-2 border transition-all active:scale-95 ${
                theme === mode
                  ? 'bg-primary text-on-primary font-bold border-primary shadow-sm'
                  : 'bg-surface-container-low text-on-surface border-outline/10 hover:bg-surface-container-high'
              }`}
            >
              <span className="material-symbols-outlined text-[18px]">
                {mode === 'system'
                  ? 'brightness_auto'
                  : mode === 'light'
                  ? 'light_mode'
                  : mode === 'dark'
                  ? 'dark_mode'
                  : 'contrast'}
              </span>
              <span>{mode}</span>
            </button>
          ))}
        </div>
      </section>

      {/* Accessibility & Scalability Controls */}
      <section aria-label="Accessibility settings" className="bg-surface-container rounded-2xl p-4 sm:p-5 border border-outline/10 space-y-4">
        <div className="flex items-center gap-2 text-on-surface font-semibold text-sm sm:text-base">
          <span className="material-symbols-outlined text-primary text-[20px]">accessibility</span>
          <h3>App Accessibility & Scalability</h3>
        </div>

        {/* Text Scaling */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 p-3 bg-surface-container-low rounded-xl border border-outline/10">
          <div>
            <span className="text-xs font-semibold text-on-surface block">Text Scaling</span>
            <span className="text-[11px] text-on-surface-variant font-mono">Dynamic typography hierarchy scale</span>
          </div>
          <div className="flex items-center gap-1 bg-surface-container-lowest p-1 rounded-lg border border-outline/10">
            {(['normal', 'large', 'xlarge'] as TextScale[]).map((scale) => (
              <button
                key={scale}
                type="button"
                onClick={() => onChangeTextScale(scale)}
                className={`h-8 px-3 rounded text-xs font-mono capitalize transition-colors ${
                  textScale === scale
                    ? 'bg-primary text-on-primary font-bold'
                    : 'text-on-surface-variant hover:text-on-surface'
                }`}
              >
                {scale}
              </button>
            ))}
          </div>
        </div>

        {/* Reduced Motion Toggle */}
        <div className="flex items-center justify-between p-3 bg-surface-container-low rounded-xl border border-outline/10">
          <div>
            <span className="text-xs font-semibold text-on-surface block">Reduced Motion</span>
            <span className="text-[11px] text-on-surface-variant font-mono">
              Minimizes all interface transitions & animations
            </span>
          </div>
          <label className="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              checked={reducedMotion}
              onChange={(e) => onToggleReducedMotion(e.target.checked)}
              className="sr-only peer"
              aria-label="Toggle reduced motion"
            />
            <div className="w-11 h-6 bg-surface-container-highest peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary" />
          </label>
        </div>

        {/* High Contrast Toggle */}
        <div className="flex items-center justify-between p-3 bg-surface-container-low rounded-xl border border-outline/10">
          <div>
            <span className="text-xs font-semibold text-on-surface block">High Contrast Outlines</span>
            <span className="text-[11px] text-on-surface-variant font-mono">
              Amplifies borders and visual separation for low vision
            </span>
          </div>
          <label className="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              checked={highContrast}
              onChange={(e) => onToggleHighContrast(e.target.checked)}
              className="sr-only peer"
              aria-label="Toggle high contrast outlines"
            />
            <div className="w-11 h-6 bg-surface-container-highest peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary" />
          </label>
        </div>
      </section>

      {/* Permissions & Services Status */}
      <section aria-label="System Services & Permissions" className="bg-surface-container rounded-2xl p-4 sm:p-5 border border-outline/10 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-on-surface font-semibold text-sm sm:text-base">
            <span className="material-symbols-outlined text-primary text-[20px]">tune</span>
            <h3>System Service Connections</h3>
          </div>
          <button
            type="button"
            onClick={onOpenPermissions}
            className="text-xs font-mono text-primary hover:underline"
          >
            Manage Privileges
          </button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs font-mono">
          <div className="p-3 bg-surface-container-low rounded-xl border border-outline/10 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className={`material-symbols-outlined text-[18px] ${
                accessibilityEnabled ? 'text-primary' : 'text-error'
              }`}>
                {accessibilityEnabled ? 'check_circle' : 'cancel'}
              </span>
              <span>Accessibility Service</span>
            </div>
            <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
              accessibilityEnabled ? 'bg-primary/20 text-primary' : 'bg-error-container text-on-error-container'
            }`}>
              {accessibilityEnabled ? 'BOUND' : 'DISENGAGED'}
            </span>
          </div>

          <div className="p-3 bg-surface-container-low rounded-xl border border-outline/10 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className={`material-symbols-outlined text-[18px] ${
                usageAccessEnabled ? 'text-primary' : 'text-outline'
              }`}>
                {usageAccessEnabled ? 'check_circle' : 'pending'}
              </span>
              <span>Usage Stats Access</span>
            </div>
            <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
              usageAccessEnabled ? 'bg-primary/20 text-primary' : 'bg-surface-container-high text-outline'
            }`}>
              {usageAccessEnabled ? 'GRANTED' : 'UNSET'}
            </span>
          </div>
        </div>
      </section>

      {/* APK & Vector Asset Generator (Section 32) */}
      <section aria-label="Vector Asset Generator" className="bg-surface-container rounded-2xl p-4 sm:p-5 border border-outline/10 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-on-surface font-semibold text-sm sm:text-base">
            <span className="material-symbols-outlined text-secondary text-[20px]">token</span>
            <h3>App Logo & Android Vector Assets</h3>
          </div>
          <span className="text-[11px] font-mono text-outline">Section 32 Spec</span>
        </div>
        <p className="text-xs text-on-surface-variant">
          Adaptive vector drawables communicating <strong className="text-on-surface">STOP + CLEAN + CONTROL</strong> without text.
        </p>

        {/* Asset Previews */}
        <div className="flex items-center justify-around p-4 bg-surface-container-lowest rounded-xl border border-outline/10">
          <div className="flex flex-col items-center gap-1.5">
            <div className="w-14 h-14 rounded-2xl bg-surface-container-high flex items-center justify-center border border-outline/15">
              <AppLogo size={36} variant="full" />
            </div>
            <span className="text-[10px] font-mono text-on-surface-variant">Full Adaptive</span>
          </div>
          <div className="flex flex-col items-center gap-1.5">
            <div className="w-14 h-14 rounded-2xl bg-surface-container-high flex items-center justify-center border border-outline/15 text-primary">
              <AppLogo size={36} variant="icon-only" />
            </div>
            <span className="text-[10px] font-mono text-on-surface-variant">Icon Only</span>
          </div>
          <div className="flex flex-col items-center gap-1.5">
            <div className="w-14 h-14 rounded-2xl bg-surface-container-high flex items-center justify-center border border-outline/15 text-white">
              <AppLogo size={36} variant="monochrome" />
            </div>
            <span className="text-[10px] font-mono text-on-surface-variant">Monochrome</span>
          </div>
        </div>

        {/* Code Tabs */}
        <div className="flex items-center gap-1 border-b border-outline/10 pb-2 overflow-x-auto text-xs font-mono">
          <button
            type="button"
            onClick={() => setActiveAssetTab('foreground')}
            className={`px-3 py-1.5 rounded-lg transition-colors whitespace-nowrap ${
              activeAssetTab === 'foreground'
                ? 'bg-primary/20 text-primary font-bold'
                : 'text-on-surface-variant hover:text-on-surface'
            }`}
          >
            ic_launcher_foreground.xml
          </button>
          <button
            type="button"
            onClick={() => setActiveAssetTab('background')}
            className={`px-3 py-1.5 rounded-lg transition-colors whitespace-nowrap ${
              activeAssetTab === 'background'
                ? 'bg-primary/20 text-primary font-bold'
                : 'text-on-surface-variant hover:text-on-surface'
            }`}
          >
            ic_launcher_background.xml
          </button>
          <button
            type="button"
            onClick={() => setActiveAssetTab('monochrome')}
            className={`px-3 py-1.5 rounded-lg transition-colors whitespace-nowrap ${
              activeAssetTab === 'monochrome'
                ? 'bg-primary/20 text-primary font-bold'
                : 'text-on-surface-variant hover:text-on-surface'
            }`}
          >
            ic_launcher_monochrome.xml
          </button>
          <button
            type="button"
            onClick={() => setActiveAssetTab('svg')}
            className={`px-3 py-1.5 rounded-lg transition-colors whitespace-nowrap ${
              activeAssetTab === 'svg'
                ? 'bg-primary/20 text-primary font-bold'
                : 'text-on-surface-variant hover:text-on-surface'
            }`}
          >
            Standalone.svg
          </button>
        </div>

        {/* Code Display Area */}
        <div className="relative bg-surface-container-lowest p-3 rounded-xl border border-outline/10 font-mono text-[11px] text-on-surface-variant max-h-48 overflow-y-auto">
          <button
            type="button"
            onClick={handleCopyAsset}
            className="absolute top-2 right-2 px-3 py-1 rounded-md bg-surface-container text-on-surface hover:bg-primary hover:text-on-primary text-[10px] font-mono font-bold transition-all shadow-sm"
          >
            {copiedAsset === activeAssetTab ? 'Copied ✓' : 'Copy XML'}
          </button>
          <pre className="pr-16 leading-tight whitespace-pre-wrap">{getAssetCode()}</pre>
        </div>
      </section>
    </div>
  );
};
