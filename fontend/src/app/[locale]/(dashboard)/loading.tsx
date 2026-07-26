import { Skeleton } from '@/components/ui/skeleton';

export default function DashboardLoading() {
  return (
    <div className="space-y-8" aria-busy="true" aria-label="Loading">
      <div className="space-y-3"><Skeleton className="h-4 w-28" /><Skeleton className="h-12 w-3/4 max-w-xl" /><Skeleton className="h-5 w-full max-w-lg" /></div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{Array.from({ length: 4 }, (_, index) => <Skeleton key={index} className="h-32 rounded-2xl" />)}</div>
      <div className="grid gap-5 lg:grid-cols-[1.35fr_.65fr]"><Skeleton className="h-72 rounded-2xl" /><Skeleton className="h-72 rounded-2xl" /></div>
    </div>
  );
}
