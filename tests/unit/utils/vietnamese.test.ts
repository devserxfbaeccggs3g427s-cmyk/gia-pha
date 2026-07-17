import { describe, expect, it } from 'vitest';
import { normalizeVietnamese } from '@/lib/utils/vietnamese';

describe('normalizeVietnamese', () => {
  it('removes Vietnamese diacritics, maps đ and normalizes whitespace and case', () => {
    expect(normalizeVietnamese('  Đặng   Thị Hồng  ')).toBe('dang thi hong');
    expect(normalizeVietnamese('CỘNG HÒA XÃ HỘI')).toBe('cong hoa xa hoi');
  });

  it('does not mutate punctuation or non-Vietnamese base characters', () => {
    expect(normalizeVietnamese('Jean-Luc, Đà Nẵng')).toBe('jean-luc, da nang');
  });
});
