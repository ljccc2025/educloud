import { http, type ApiEnvelope } from './http';

export interface EmailChannelStatus {
  provider: string;
  host: string;
  port: number;
  username: string;
  from: string;
  sslEnabled: boolean;
  passwordConfigured: boolean;
}

export interface PublishNotificationRequest {
  title: string;
  content: string;
  kind?: string;
  targetType?: string;
  recipientIds?: string[];
  actionLabel?: string;
  actionPath?: string;
  sendEmail?: boolean;
}

export const notificationAdminApi = {
  getEmailChannelStatus: async () => {
    const res = await http.get<ApiEnvelope<EmailChannelStatus>>('/notification-channels/email/status');
    return res.data;
  },
  testSendEmail: async (customSubject?: string) => {
    const res = await http.post<ApiEnvelope<null>>('/notification-channels/email/test-send', { customSubject });
    return res.data;
  },
  publishNotification: async (req: PublishNotificationRequest) => {
    const res = await http.post<ApiEnvelope<any>>('/admin/notifications', req);
    return res.data;
  },
};
