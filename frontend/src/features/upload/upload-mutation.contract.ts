export type UploadMutationState =
  | 'idle'
  | 'fileSelected'
  | 'validating'
  | 'ready'
  | 'uploading'
  | 'processing'
  | 'success'
  | 'error'
  | 'canceled'
  | 'retrying'

export type UploadMutationPolicy = {
  createsProductType: 'RAW_MEDIA'
  optimisticCanonicalProduct: false
  exposeStorageReference: false
  exposeSignedUrl: false
  triggerRenderSubmission: false
  safePreflightExposure: 'none-in-app'
}

export const UPLOAD_MUTATION_POLICY: UploadMutationPolicy = {
  createsProductType: 'RAW_MEDIA',
  optimisticCanonicalProduct: false,
  exposeStorageReference: false,
  exposeSignedUrl: false,
  triggerRenderSubmission: false,
  safePreflightExposure: 'none-in-app',
}
