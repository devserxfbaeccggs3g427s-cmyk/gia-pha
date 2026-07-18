import { create } from 'zustand';

export interface TreeViewport {
  x: number;
  y: number;
  zoom: number;
}

interface TreeUiState {
  activeTreeId?: string;
  viewport: TreeViewport;
  selectedNodeId?: string;
  setActiveTree: (treeId: string) => void;
  setViewport: (viewport: TreeViewport) => void;
  setZoom: (zoom: number) => void;
  setPan: (pan: Pick<TreeViewport, 'x' | 'y'>) => void;
  selectNode: (nodeId?: string) => void;
  reset: (treeId?: string) => void;
}

const initialViewport: TreeViewport = { x: 0, y: 0, zoom: 1 };

export const useTreeUiStore = create<TreeUiState>((set) => ({
  viewport: initialViewport,
  setActiveTree: (activeTreeId) => set((state) => state.activeTreeId === activeTreeId
    ? state
    : { activeTreeId, viewport: initialViewport, selectedNodeId: undefined }),
  setViewport: (viewport) => set({ viewport }),
  setZoom: (zoom) => set((state) => ({ viewport: { ...state.viewport, zoom } })),
  setPan: ({ x, y }) => set((state) => ({ viewport: { ...state.viewport, x, y } })),
  selectNode: (selectedNodeId) => set({ selectedNodeId }),
  reset: (activeTreeId) => set({ activeTreeId, viewport: initialViewport, selectedNodeId: undefined })
}));

