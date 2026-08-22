import { http, type ApiEnvelope } from './http';

export interface UploadSession { sessionId: number; uploadUrl: string; expiresInSeconds: number }

export async function createUploadSession(contentType: string, originalName?: string): Promise<UploadSession> {
  const resp = await http.post<ApiEnvelope<UploadSession>>('/file-upload-sessions', { contentType, originalName });
  return resp.data.data;
}

export async function uploadToPresigned(url: string, file: File): Promise<void> {
  const resp = await fetch(url, { method: 'PUT', headers: { 'Content-Type': file.type }, body: file });
  if (!resp.ok) throw new Error('上传失败，请重试');
}

export async function completeUpload(sessionId: number): Promise<number> {
  const resp = await http.post<ApiEnvelope<{ fileId: number }>>(`/file-upload-sessions/${sessionId}/complete`);
  return resp.data.data.fileId;
}

export async function uploadAvatar(file: File): Promise<number> {
  const session = await createUploadSession(file.type, file.name);
  await uploadToPresigned(session.uploadUrl, file);
  return completeUpload(session.sessionId);
}
