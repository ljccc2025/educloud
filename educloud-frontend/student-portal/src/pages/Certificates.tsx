import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Award, CalendarDays, Hash } from 'lucide-react';
import { certificateApi } from '@/services/api';
import type { Certificate } from '@/types';
import dayjs from 'dayjs';

/** 完课证书页（角色化动态流阶段 4）：真实 API GET /api/v1/content/certificates。 */
export default function Certificates() {
  const [certificates, setCertificates] = useState<Certificate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    certificateApi
      .getMyCertificates()
      .then((list) => {
        if (!cancelled) setCertificates(list);
      })
      .catch(() => {
        if (!cancelled) setError('证书加载失败，请稍后重试');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-10">
        <span className="section-label mb-3">学习成果</span>
        <h1 className="display-heading text-4xl md:text-5xl mt-3">我的证书</h1>
        <p className="text-ink-500 mt-3">完成课程学习后自动颁发完课证书，可凭证书编号查验</p>
      </div>

      {error && (
        <div className="mb-6 flex items-center justify-between rounded-xl bg-red-50 p-4 text-sm text-red-700 border border-red-200">
          <span>{error}</span>
          <button type="button" onClick={() => setError(null)} className="text-xs font-semibold underline">
            关闭
          </button>
        </div>
      )}

      {loading ? (
        <p className="text-sm text-ink-400 text-center py-16">证书加载中…</p>
      ) : certificates.length === 0 ? (
        <div className="card-editorial px-6 py-16 text-center">
          <Award size={40} className="mx-auto text-ink-200 mb-4" strokeWidth={1.2} />
          <p className="text-sm text-ink-500 mb-4">还没有获得证书，完成一门课程即可解锁</p>
          <Link to="/courses" className="btn-primary text-sm">
            去选课学习
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {certificates.map((cert) => (
            <div key={cert.certNo} className="card-editorial group relative overflow-hidden p-6">
              <span
                aria-hidden="true"
                className="pointer-events-none absolute -top-4 -right-2 font-display text-7xl font-black text-indigo-800/[0.05] leading-none select-none"
              >
                🎓
              </span>
              <div className="flex items-start justify-between gap-3 mb-5">
                <div className="w-11 h-11 flex items-center justify-center rounded-xl bg-amber-50 text-amber-600">
                  <Award size={22} strokeWidth={1.5} />
                </div>
                <span className="px-2 py-0.5 text-[10px] font-medium rounded-full bg-indigo-50 text-indigo-700">
                  完课证书
                </span>
              </div>
              <p className="font-display text-lg font-bold text-ink-900 line-clamp-2 min-h-[3.5rem]">
                {cert.courseTitle || '课程证书'}
              </p>
              <div className="mt-4 pt-4 border-t border-ink-100 space-y-2">
                <p className="flex items-center gap-2 text-xs text-ink-500">
                  <Hash size={13} className="text-ink-400 flex-shrink-0" />
                  <span className="truncate" title={cert.certNo}>
                    证书编号：{cert.certNo}
                  </span>
                </p>
                <p className="flex items-center gap-2 text-xs text-ink-500">
                  <CalendarDays size={13} className="text-ink-400 flex-shrink-0" />
                  颁发时间：{dayjs(cert.issuedAt).isValid() ? dayjs(cert.issuedAt).format('YYYY-MM-DD HH:mm') : '-'}
                </p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
