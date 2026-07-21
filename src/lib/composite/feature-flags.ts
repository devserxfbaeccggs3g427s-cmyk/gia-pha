export type CompositeRolloutStage = 'DISABLED' | 'INTERNAL' | 'PILOT' | 'GENERAL';

export class CompositeFeatureError extends Error {
  readonly code = 'COMPOSITE_FEATURE_DISABLED';
  constructor(public readonly feature: string) { super(`Composite ${feature} is not enabled`); this.name = 'CompositeFeatureError'; }
}

export function compositeRolloutStage(): CompositeRolloutStage {
  const value = (process.env.COMPOSITE_TREES_ROLLOUT_STAGE ?? (process.env.COMPOSITE_TREES_ENABLED === 'false' ? 'DISABLED' : 'GENERAL')).toUpperCase();
  return value === 'INTERNAL' || value === 'PILOT' || value === 'GENERAL' ? value : 'DISABLED';
}

export function requireCompositeFeature(feature: 'trees' | 'export' | 'import' | 'cache' | 'sharing'): void {
  if (compositeRolloutStage() === 'DISABLED' || process.env[`COMPOSITE_${feature.toUpperCase()}_ENABLED`] === 'false') {
    throw new CompositeFeatureError(feature);
  }
}

export function emitCompositeMetric(name: string, values: Record<string, string | number | boolean>): void {
  if (process.env.NODE_ENV !== 'test') console.info('composite_metric', { name, ...values, timestamp: new Date().toISOString() });
}
