export type ApiEnvelope<T> = {
  success: boolean;
  timestamp: string;
  data: T | null;
  error: { code: string; message: string } | null;
};

export async function readApiResponse<T>(response: Response): Promise<T> {
  // Feature code receives data or an Error, never the transport envelope itself.
  const envelope = await response.json() as ApiEnvelope<T>;
  if (!response.ok || !envelope.success) throw new Error(envelope.error?.message ?? "The request could not be completed.");
  return envelope.data as T;
}
