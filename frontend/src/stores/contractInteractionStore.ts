import { create } from 'zustand'

interface ContractInteractionState {
  signedTermsByContractId: Map<string, string>
  markSigned: (contractId: string, termsFingerprint: string) => void
}

export const useContractInteractionStore = create<ContractInteractionState>((set) => ({
  signedTermsByContractId: new Map(),
  markSigned: (contractId, termsFingerprint) =>
    set((state) => {
      const next = new Map(state.signedTermsByContractId)
      next.set(contractId, termsFingerprint)
      return { signedTermsByContractId: next }
    }),
}))
