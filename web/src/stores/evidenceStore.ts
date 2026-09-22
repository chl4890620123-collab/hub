import { create } from 'zustand';
import type { EvidenceView } from '@/api/types';

interface EvidenceState {
  isOpen: boolean;
  title: string | null;
  items: EvidenceView[];
  open: (title: string, items: EvidenceView[]) => void;
  close: () => void;
}

export const useEvidenceStore = create<EvidenceState>((set) => ({
  isOpen: false,
  title: null,
  items: [],
  open: (title, items) => set({ isOpen: true, title, items }),
  close: () => set({ isOpen: false, title: null, items: [] }),
}));
