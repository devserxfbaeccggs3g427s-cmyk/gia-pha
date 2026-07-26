import { LanguageSwitcher } from '@/components/i18n/language-switcher';

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <main>
      <div className="locale-switcher-slot"><LanguageSwitcher /></div>
      {children}
    </main>
  );
}
