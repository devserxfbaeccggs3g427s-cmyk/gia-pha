/**
 * Produces a stable, accent-insensitive representation of Vietnamese text.
 *
 * NFD separates most accents from their base character. Vietnamese `đ`/`Đ`
 * needs an explicit replacement because it is not decomposed by Unicode.
 */
export function normalizeVietnamese(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[đĐ]/g, 'd')
    .toLowerCase()
    .trim()
    .replace(/\s+/g, ' ');
}

export default normalizeVietnamese;
