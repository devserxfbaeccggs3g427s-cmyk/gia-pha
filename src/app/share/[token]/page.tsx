import { notFound } from 'next/navigation';
import type { Member } from '@/data/types';
import { ShareLinkServiceError, shareLinkService } from '@/lib/services/share-link-service';
import styles from './share.module.css';

export const dynamic = 'force-dynamic';
export const metadata = {
  title: 'Gia phả được chia sẻ',
  robots: { index: false, follow: false, noarchive: true }
};

export default async function SharedTreePage({ params }: { params: { token: string } }) {
  let data;
  try {
    data = await shareLinkService.getSharedTree(params.token);
  } catch (error) {
    if (error instanceof ShareLinkServiceError) notFound();
    throw error;
  }

  const memberById = new Map(data.members.map((member) => [member.id, member]));
  const generations = new Set(data.members.flatMap((member) => member.generation === undefined ? [] : [member.generation]));
  const expiration = formatDateTime(data.shareLink.expiresAt);

  return (
    <main className={styles.shell}>
      <div className={styles.glow} aria-hidden="true" />
      <section className={styles.hero}>
        <div>
          <div className={styles.eyebrow}><span /> Bản chia sẻ chỉ xem</div>
          <h1>{data.tree.name}</h1>
          {data.tree.description ? <p className={styles.description}>{data.tree.description}</p> : null}
        </div>
        <div className={styles.expiration}>
          <span>Liên kết có hiệu lực đến</span>
          <strong>{expiration}</strong>
        </div>
      </section>

      <section className={styles.stats} aria-label="Tổng quan gia phả">
        <Stat value={data.members.length} label="Thành viên" />
        <Stat value={generations.size} label="Thế hệ" />
        <Stat value={countCanonicalRelationships(data.relationships)} label="Mối quan hệ" />
        <Stat value={data.events.length} label="Sự kiện" />
      </section>

      <section className={styles.section}>
        <div className={styles.sectionHeading}>
          <div><span>Thành viên</span><h2>Những người trong gia phả</h2></div>
          <p>Sắp xếp theo thế hệ, từ bậc tổ tiên đến con cháu.</p>
        </div>
        {data.members.length ? (
          <div className={styles.memberGrid}>
            {[...data.members].sort(compareMembers).map((member) => (
              <article className={styles.memberCard} key={member.id}>
                <div className={styles.avatar} data-gender={member.gender} aria-hidden="true">
                  {initials(member.fullName)}
                </div>
                <div className={styles.memberBody}>
                  <div className={styles.memberTopline}>
                    <span>{member.generation === undefined ? 'Chưa xác định thế hệ' : `Thế hệ ${member.generation + 1}`}</span>
                    <i data-alive={member.isAlive}>{member.isAlive ? 'Còn sống' : 'Đã mất'}</i>
                  </div>
                  <h3>{member.fullName}</h3>
                  <p>{member.occupation || member.placeOfBirth || 'Thông tin đang được cập nhật'}</p>
                  <div className={styles.life}>{formatLife(member)}</div>
                </div>
              </article>
            ))}
          </div>
        ) : <EmptyState message="Gia phả chưa có thành viên." />}
      </section>

      <section className={styles.twoColumns}>
        <div className={styles.panel}>
          <div className={styles.panelTitle}><span>Liên kết gia đình</span><h2>Mối quan hệ</h2></div>
          <div className={styles.list}>
            {canonicalRelationships(data.relationships).slice(0, 12).map((relationship) => (
              <div className={styles.listItem} key={relationship.id}>
                <div><strong>{memberById.get(relationship.sourceMemberId)?.fullName ?? 'Không xác định'}</strong><span>{relationshipLabel(relationship.type)}</span></div>
                <b aria-hidden="true">→</b>
                <strong>{memberById.get(relationship.targetMemberId)?.fullName ?? 'Không xác định'}</strong>
              </div>
            ))}
            {!data.relationships.length ? <EmptyState message="Chưa có mối quan hệ." compact /> : null}
          </div>
        </div>

        <div className={styles.panel}>
          <div className={styles.panelTitle}><span>Dấu mốc</span><h2>Sự kiện gia đình</h2></div>
          <div className={styles.timeline}>
            {[...data.events].sort((a, b) => b.eventDate.localeCompare(a.eventDate)).slice(0, 10).map((event) => (
              <article key={event.id}>
                <time>{formatDate(event.eventDate)}</time>
                <div><h3>{event.title}</h3><p>{event.location || event.description || 'Sự kiện gia đình'}</p></div>
              </article>
            ))}
            {!data.events.length ? <EmptyState message="Chưa có sự kiện." compact /> : null}
          </div>
        </div>
      </section>

      <footer className={styles.footer}>
        <div><span className={styles.lock}>◆</span><strong>Quyền riêng tư được bảo vệ</strong></div>
        <p>Đây là bản xem chỉ đọc. Bạn không thể chỉnh sửa hoặc tải dữ liệu từ liên kết này.</p>
      </footer>
    </main>
  );
}

function Stat({ value, label }: { value: number; label: string }) {
  return <article><strong>{new Intl.NumberFormat('vi-VN').format(value)}</strong><span>{label}</span></article>;
}

function EmptyState({ message, compact = false }: { message: string; compact?: boolean }) {
  return <div className={compact ? styles.emptyCompact : styles.empty}>{message}</div>;
}

function compareMembers(left: Member, right: Member): number {
  return (left.generation ?? Number.MAX_SAFE_INTEGER) - (right.generation ?? Number.MAX_SAFE_INTEGER) || left.fullName.localeCompare(right.fullName, 'vi');
}

function initials(name: string): string {
  return name.trim().split(/\s+/).slice(-2).map((part) => part[0]?.toUpperCase()).join('');
}

function formatLife(member: Member): string {
  const birth = member.dateOfBirth ? new Date(member.dateOfBirth).getUTCFullYear() : null;
  const death = member.dateOfDeath ? new Date(member.dateOfDeath).getUTCFullYear() : null;
  if (birth && death) return `${birth} — ${death}`;
  if (birth) return `Sinh năm ${birth}`;
  if (death) return `Mất năm ${death}`;
  return 'Chưa rõ năm sinh';
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(new Date(value));
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZoneName: 'short' }).format(new Date(value));
}

function canonicalRelationships<T extends { id: string; sourceMemberId: string; targetMemberId: string }>(relationships: T[]): T[] {
  const seen = new Set<string>();
  return relationships.filter((relationship) => {
    const key = [relationship.sourceMemberId, relationship.targetMemberId].sort().join(':');
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function countCanonicalRelationships<T extends { id: string; sourceMemberId: string; targetMemberId: string }>(relationships: T[]): number {
  return canonicalRelationships(relationships).length;
}

function relationshipLabel(type: string): string {
  return ({ PARENT_CHILD: 'Cha/mẹ · con', SPOUSE: 'Vợ · chồng', SIBLING: 'Anh/chị/em', ADOPTED: 'Nuôi dưỡng', CUSTOM: 'Quan hệ gia đình' } as Record<string, string>)[type] ?? 'Quan hệ gia đình';
}
