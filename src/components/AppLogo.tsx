import React from 'react';

interface AppLogoProps {
  className?: string;
  size?: number;
  variant?: 'full' | 'icon-only' | 'monochrome' | 'badge';
}

export const AppLogo: React.FC<AppLogoProps> = ({
  className = '',
  size = 32,
  variant = 'icon-only',
}) => {
  if (variant === 'monochrome') {
    return (
      <svg
        width={size}
        height={size}
        viewBox="0 0 100 100"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        className={`shrink-0 ${className}`}
        aria-hidden="true"
      >
        {/* Android Monochrome Adaptive Vector: STOP + CLEAN + CONTROL */}
        <path
          d="M50 14L22 26V52C22 71.5 34 85.5 50 90C66 85.5 78 71.5 78 52V26L50 14Z"
          fill="none"
          stroke="currentColor"
          strokeWidth="6"
          strokeLinejoin="round"
        />
        {/* Octagonal Stop Chamfer */}
        <path
          d="M38 34H62L70 42V58L62 66H38L30 58V42L38 34Z"
          fill="currentColor"
          fillOpacity="0.25"
          stroke="currentColor"
          strokeWidth="5"
          strokeLinejoin="round"
        />
        {/* Control Center Pill / Pause bar */}
        <rect
          x="41"
          y="46"
          width="18"
          height="8"
          rx="4"
          fill="currentColor"
        />
      </svg>
    );
  }

  if (variant === 'full') {
    return (
      <svg
        width={size}
        height={size}
        viewBox="0 0 120 120"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        className={`shrink-0 ${className}`}
        aria-label="App Controller Logo"
      >
        {/* Squircle Adaptive Background */}
        <rect width="120" height="120" rx="36" fill="#101417" />
        <rect width="120" height="120" rx="36" stroke="#4edea3" strokeWidth="2" strokeOpacity="0.25" />
        
        {/* Outer Shield - Security & Guardrails */}
        <path
          d="M60 22L30 35V62C30 83.5 43.5 98.5 60 103.5C76.5 98.5 90 83.5 90 62V35L60 22Z"
          fill="#003824"
          fillOpacity="0.4"
          stroke="#4edea3"
          strokeWidth="5.5"
          strokeLinejoin="round"
        />

        {/* Middle Octagonal Stop Node - Clean & Halt Process */}
        <path
          d="M48 43H72L80 51V69L72 77H48L40 69V51L48 43Z"
          fill="#10b981"
          fillOpacity="0.3"
          stroke="#10b981"
          strokeWidth="4.5"
          strokeLinejoin="round"
        />

        {/* Center Control Core */}
        <rect
          x="50"
          y="56"
          width="20"
          height="8"
          rx="4"
          fill="#101417"
        />
      </svg>
    );
  }

  // Default: 'icon-only' or 'badge'
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={`shrink-0 ${className}`}
      aria-label="App Controller Security Emblem"
    >
      {/* Outer Shield: Green #4edea3 / #10b981 */}
      <path
        d="M50 14L22 26V52C22 71.5 34 85.5 50 90C66 85.5 78 71.5 78 52V26L50 14Z"
        fill="#003824"
        fillOpacity="0.4"
        stroke="#4edea3"
        strokeWidth="6"
        strokeLinejoin="round"
      />

      {/* Octagon: STOP */}
      <path
        d="M38 34H62L70 42V58L62 66H38L30 58V42L38 34Z"
        fill="#10b981"
        fillOpacity="0.35"
        stroke="#10b981"
        strokeWidth="5"
        strokeLinejoin="round"
      />

      {/* Control core */}
      <rect
        x="42"
        y="46"
        width="16"
        height="8"
        rx="4"
        fill="#101417"
        className="fill-surface"
      />
    </svg>
  );
};

export const LAUNCHER_ASSET_TEMPLATES = {
  ic_launcher_foreground: `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Outer Security Shield -->
    <path
        android:strokeColor="#4EDEA3"
        android:strokeWidth="5.5"
        android:strokeLineJoin="round"
        android:fillColor="#15003824"
        android:pathData="M54,20 L26,32 V56 C26,75.5 38.5,89 54,93.5 C69.5,89 82,75.5 82,56 V32 Z" />
    <!-- Octagonal Stop Node -->
    <path
        android:strokeColor="#10B981"
        android:strokeWidth="4.5"
        android:strokeLineJoin="round"
        android:fillColor="#3310B981"
        android:pathData="M43,40 H65 L72,47 V63 L65,70 H43 L36,63 V47 Z" />
    <!-- Control Core -->
    <path
        android:fillColor="#101417"
        android:pathData="M46,52 H62 C64.2,52 66,53.8 66,56 C66,58.2 64.2,60 62,60 H46 C43.8,60 42,58.2 42,56 C42,53.8 43.8,52 46,52 Z" />
</vector>`,

  ic_launcher_background: `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#101417"
        android:pathData="M0,0 H108 V108 H0 Z" />
    <!-- Subtle depth gradient rings -->
    <path
        android:strokeColor="#1C2023"
        android:strokeWidth="1"
        android:fillColor="#00000000"
        android:pathData="M54,18 A36,36 0 1,0 54,90 A36,36 0 1,0 54,18" />
</vector>`,

  ic_launcher_monochrome: `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="6"
        android:strokeLineJoin="round"
        android:fillColor="#00000000"
        android:pathData="M54,20 L26,32 V56 C26,75.5 38.5,89 54,93.5 C69.5,89 82,75.5 82,56 V32 Z" />
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="5"
        android:strokeLineJoin="round"
        android:fillColor="#44FFFFFF"
        android:pathData="M43,40 H65 L72,47 V63 L65,70 H43 L36,63 V47 Z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M46,52 H62 C64.2,52 66,53.8 66,56 C66,58.2 64.2,60 62,60 H46 C43.8,60 42,58.2 42,56 C42,53.8 43.8,52 46,52 Z" />
</vector>`,
};
