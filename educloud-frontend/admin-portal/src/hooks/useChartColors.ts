import { useThemeStore } from '../stores/useThemeStore';

export interface ChartColors {
  grid: string;
  text: string;
  axis: string;
  tooltipBg: string;
  tooltipBorder: string;
  tooltipText: string;
  tooltipLabel: string;
  cursor: string;
  primary: string;
  secondary: string;
  legend: string;
  pie: string[];
}

const readVar = (name: string) => {
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim();
  return value ? `rgb(${value})` : '';
};

export function useChartColors(): ChartColors {
  // Subscribe to theme so component re-renders on toggle
  useThemeStore((s) => s.theme);

  return {
    grid: readVar('--chart-grid'),
    text: readVar('--chart-text'),
    axis: readVar('--chart-axis'),
    tooltipBg: readVar('--chart-tooltip-bg'),
    tooltipBorder: readVar('--chart-tooltip-border'),
    tooltipText: readVar('--chart-tooltip-text'),
    tooltipLabel: readVar('--chart-tooltip-label'),
    cursor: `rgb(${getComputedStyle(document.documentElement).getPropertyValue('--chart-cursor').trim()} / 0.08)`,
    primary: readVar('--chart-primary'),
    secondary: readVar('--chart-secondary'),
    legend: readVar('--chart-legend'),
    pie: [
      readVar('--chart-pie-1'),
      readVar('--chart-pie-2'),
      readVar('--chart-pie-3'),
      readVar('--chart-pie-4'),
    ],
  };
}
