import type { Contract } from '../types/contract'

export const getCurrentTermsSignatureState = (contract: Contract, userId?: string): boolean | undefined => {
  if (!userId || contract.signatures === undefined) return undefined

  return contract.signatures.some((signature) => {
    if (signature.userId !== userId) return false
    if (contract.termsRevision === undefined || signature.termsRevision === undefined) return true
    return signature.termsRevision === contract.termsRevision
  })
}

export const hasAuthoritativeSignatureState = (contract: Contract) => contract.signatures !== undefined
