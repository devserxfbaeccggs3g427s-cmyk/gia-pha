import { DashboardShell } from '@/components/layout/dashboard-shell';

// Dashboard HTML is rendered for the authenticated request. Client-side
// navigation then reuses the App Router shell and streams only route payloads.
export const dynamic = 'force-dynamic';

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return <DashboardShell>{children}</DashboardShell>;
}
