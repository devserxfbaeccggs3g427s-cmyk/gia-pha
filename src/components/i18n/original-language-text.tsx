import React, { type ReactNode } from 'react';

/**
 * Isolates user-authored genealogy content from UI direction and automatic
 * translation. Use for names, biographies and other data stored verbatim.
 */
export function OriginalLanguageText({ children }: { children: ReactNode }) {
  return <bdi dir="auto" translate="no">{children}</bdi>;
}
