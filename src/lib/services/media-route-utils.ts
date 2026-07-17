import { getMediaMetadata, getTrees } from '@/lib/blob/readers';

export async function findMediaTree(request: Request, mediaId: string): Promise<string | null> {
  const requestedTreeId = new URL(request.url).searchParams.get('treeId');
  if (requestedTreeId) {
    return (await getMediaMetadata(requestedTreeId)).some((item) => item.id === mediaId)
      ? requestedTreeId
      : null;
  }
  for (const tree of await getTrees()) {
    if ((await getMediaMetadata(tree.id)).some((item) => item.id === mediaId)) return tree.id;
  }
  return null;
}
