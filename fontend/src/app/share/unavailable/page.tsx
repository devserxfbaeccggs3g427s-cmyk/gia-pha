import Link from 'next/link';

export const metadata = { title: 'Liên kết không khả dụng', robots: { index: false, follow: false } };

export default function UnavailableSharePage({ searchParams }: { searchParams: { reason?: string } }) {
  const expired = searchParams.reason === 'expired';
  return (
    <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24, color: '#183129', background: 'linear-gradient(135deg, #faf8f2, #edf3ed)' }}>
      <section style={{ width: 'min(100%, 520px)', padding: '44px', border: '1px solid rgba(30,70,50,.13)', borderRadius: 22, background: 'rgba(255,255,255,.75)', boxShadow: '0 24px 70px rgba(30,60,45,.08)', textAlign: 'center' }}>
        <div style={{ color: '#a36d24', fontSize: 12, fontWeight: 700, letterSpacing: '.13em', textTransform: 'uppercase' }}>Gia phả gia đình</div>
        <h1 style={{ margin: '14px 0 10px', fontFamily: 'Georgia, serif', fontSize: 38, fontWeight: 500 }}>{expired ? 'Liên kết đã hết hạn' : 'Liên kết không khả dụng'}</h1>
        <p style={{ margin: '0 auto 26px', color: '#66736d', lineHeight: 1.65 }}>{expired ? 'Thời hạn xem gia phả này đã kết thúc. Hãy liên hệ người chia sẻ để nhận liên kết mới.' : 'Liên kết có thể không đúng hoặc đã được người chia sẻ thu hồi.'}</p>
        <Link href="/" style={{ display: 'inline-block', padding: '11px 18px', borderRadius: 10, color: 'white', background: '#276749', fontWeight: 650, textDecoration: 'none' }}>Về trang chủ</Link>
      </section>
    </main>
  );
}
