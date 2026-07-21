import { CompositeConfigError } from './composite-config-service';
import { treeService } from './tree-service';
export async function requireStandaloneMutationTarget(treeId: string): Promise<void> { const tree = await treeService.getTree(treeId); if ((tree.kind ?? 'STANDALONE') === 'COMPOSITE') throw new CompositeConfigError('COMPOSITE_READ_ONLY', 'Composite tree domain data is read-only'); }
