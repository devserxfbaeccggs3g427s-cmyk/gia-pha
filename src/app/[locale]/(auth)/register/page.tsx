import type { Metadata } from 'next';
import { RegisterForm } from '@/components/auth/register-form';

export const metadata: Metadata = {
  title: 'Đăng ký | Quản lý gia phả'
};

export default function RegisterPage({ params }: { params: { locale: string } }) {
  return <RegisterForm locale={params.locale === 'en' ? 'en' : 'vi'} />;
}

