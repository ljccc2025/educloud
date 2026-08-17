import { Upload, Shield, Mail, Server, Settings2, type LucideIcon } from 'lucide-react';
import type { SystemConfig } from '../types';

export type ConfigSection = 'basic' | 'email' | 'storage' | 'security';

interface ConfigFormProps {
  value: SystemConfig;
  onChange: (config: SystemConfig) => void;
  section: ConfigSection;
}

export default function ConfigForm({ value, onChange, section }: ConfigFormProps) {
  const update = <K extends keyof SystemConfig>(key: K, val: SystemConfig[K]) => {
    onChange({ ...value, [key]: val });
  };

  return (
    <div className="space-y-6">
      {section === 'basic' && (
        <ConfigCard icon={Settings2} title="基本设置" description="平台基础信息与品牌配置">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            <Field label="站点名称" required>
              <input
                className="input-field"
                value={value.siteName}
                onChange={(e) => update('siteName', e.target.value)}
              />
            </Field>
            <Field label="备案号">
              <input
                className="input-field"
                value={value.icp}
                onChange={(e) => update('icp', e.target.value)}
              />
            </Field>
            <Field label="站点描述" full>
              <textarea
                className="input-field resize-none"
                rows={3}
                value={value.siteDescription}
                onChange={(e) => update('siteDescription', e.target.value)}
              />
            </Field>
            <Field label="站点 Logo" full>
              <div className="flex items-center gap-4">
                <div className="w-20 h-20 border border-dashed border-ink-300 flex items-center justify-center bg-ink-50">
                  {value.logoUrl ? (
                    <img src={value.logoUrl} alt="logo" className="w-full h-full object-contain" />
                  ) : (
                    <Upload size={24} className="text-ink-300" />
                  )}
                </div>
                <div>
                  <label className="btn-outline cursor-pointer">
                    <Upload size={14} />
                    上传 Logo
                    <input
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (file) {
                          const reader = new FileReader();
                          reader.onload = () => update('logoUrl', reader.result as string);
                          reader.readAsDataURL(file);
                        }
                      }}
                    />
                  </label>
                  <p className="text-xs text-ink-400 mt-2">建议尺寸 200×200，PNG 格式，不超过 2MB</p>
                </div>
              </div>
            </Field>
          </div>
        </ConfigCard>
      )}

      {section === 'email' && (
        <ConfigCard icon={Mail} title="邮件配置" description="SMTP 邮件发送服务设置">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            <Field label="SMTP 服务器" required>
              <input
                className="input-field"
                value={value.smtpHost}
                onChange={(e) => update('smtpHost', e.target.value)}
              />
            </Field>
            <Field label="SMTP 端口" required>
              <input
                type="number"
                className="input-field"
                value={value.smtpPort}
                onChange={(e) => update('smtpPort', Number(e.target.value))}
              />
            </Field>
            <Field label="发件人邮箱" required>
              <input
                className="input-field"
                value={value.senderEmail}
                onChange={(e) => update('senderEmail', e.target.value)}
              />
            </Field>
            <Field label="发件人名称">
              <input
                className="input-field"
                value={value.senderName}
                onChange={(e) => update('senderName', e.target.value)}
              />
            </Field>
            <Field label="SMTP 用户名" required>
              <input
                className="input-field"
                value={value.smtpUser}
                onChange={(e) => update('smtpUser', e.target.value)}
              />
            </Field>
            <Field label="SMTP 密码" required>
              <input
                type="password"
                className="input-field"
                value={value.smtpPassword}
                onChange={(e) => update('smtpPassword', e.target.value)}
              />
            </Field>
          </div>
          <div className="mt-5 pt-5 border-t border-ink-100">
            <button className="btn-outline" type="button">
              <Mail size={14} />
              发送测试邮件
            </button>
          </div>
        </ConfigCard>
      )}

      {section === 'storage' && (
        <ConfigCard icon={Server} title="存储配置" description="MinIO 对象存储服务连接设置">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            <Field label="MinIO 端点" required>
              <input
                className="input-field"
                value={value.minioEndpoint}
                onChange={(e) => update('minioEndpoint', e.target.value)}
              />
            </Field>
            <Field label="MinIO 端口" required>
              <input
                type="number"
                className="input-field"
                value={value.minioPort}
                onChange={(e) => update('minioPort', Number(e.target.value))}
              />
            </Field>
            <Field label="Access Key" required>
              <input
                className="input-field"
                value={value.minioAccessKey}
                onChange={(e) => update('minioAccessKey', e.target.value)}
              />
            </Field>
            <Field label="Secret Key" required>
              <input
                type="password"
                className="input-field"
                value={value.minioSecretKey}
                onChange={(e) => update('minioSecretKey', e.target.value)}
              />
            </Field>
            <Field label="存储桶名称" required>
              <input
                className="input-field"
                value={value.minioBucket}
                onChange={(e) => update('minioBucket', e.target.value)}
              />
            </Field>
            <Field label="启用 SSL">
              <label className="inline-flex items-center gap-2 cursor-pointer h-[46px]">
                <input
                  type="checkbox"
                  className="w-4 h-4 accent-indigo-800"
                  checked={value.minioUseSSL}
                  onChange={(e) => update('minioUseSSL', e.target.checked)}
                />
                <span className="text-sm text-ink-700">使用 HTTPS 加密连接</span>
              </label>
            </Field>
          </div>
          <div className="mt-5 pt-5 border-t border-ink-100">
            <button className="btn-outline" type="button">
              <Server size={14} />
              测试连接
            </button>
          </div>
        </ConfigCard>
      )}

      {section === 'security' && (
        <ConfigCard icon={Shield} title="安全设置" description="JWT 认证与密码策略配置">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            <Field label="JWT 密钥" required full>
              <input
                type="password"
                className="input-field font-mono"
                value={value.jwtSecret}
                onChange={(e) => update('jwtSecret', e.target.value)}
              />
            </Field>
            <Field label="Token 过期时间（秒）" required>
              <input
                type="number"
                className="input-field"
                value={value.jwtExpiration}
                onChange={(e) => update('jwtExpiration', Number(e.target.value))}
              />
            </Field>
            <Field label="登录失败锁定次数" required>
              <input
                type="number"
                className="input-field"
                value={value.loginAttemptLimit}
                onChange={(e) => update('loginAttemptLimit', Number(e.target.value))}
              />
            </Field>
            <Field label="密码最小长度" required>
              <input
                type="number"
                className="input-field"
                value={value.passwordMinLength}
                onChange={(e) => update('passwordMinLength', Number(e.target.value))}
              />
            </Field>
            <Field label="邮箱验证">
              <label className="inline-flex items-center gap-2 cursor-pointer h-[46px]">
                <input
                  type="checkbox"
                  className="w-4 h-4 accent-indigo-800"
                  checked={value.requireEmailVerify}
                  onChange={(e) => update('requireEmailVerify', e.target.checked)}
                />
                <span className="text-sm text-ink-700">新用户注册需邮箱验证</span>
              </label>
            </Field>
          </div>
        </ConfigCard>
      )}
    </div>
  );
}

function ConfigCard({
  icon: Icon,
  title,
  description,
  children,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <div className="card-editorial p-6 md:p-8">
      <div className="flex items-start gap-4 mb-6 pb-5 border-b border-ink-100">
        <span className="flex items-center justify-center w-11 h-11 bg-indigo-800 text-paper shrink-0">
          <Icon size={20} />
        </span>
        <div>
          <h3 className="font-display text-xl font-700 text-ink-900">{title}</h3>
          <p className="text-sm text-ink-500 mt-0.5">{description}</p>
        </div>
      </div>
      {children}
    </div>
  );
}

function Field({
  label,
  required,
  full,
  children,
}: {
  label: string;
  required?: boolean;
  full?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className={full ? 'md:col-span-2' : ''}>
      <label className="block text-sm font-medium text-ink-700 mb-2">
        {label}
        {required && <span className="text-amber-600 ml-1">*</span>}
      </label>
      {children}
    </div>
  );
}
