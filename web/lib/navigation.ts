export const LIST_PREFETCH_BUDGET = 4 as const;

/** Keep list navigation responsive without prefetching every result route in view. */
export function shouldPrefetchListItem(index: number): boolean {
  return Number.isInteger(index) && index >= 0 && index < LIST_PREFETCH_BUDGET;
}
