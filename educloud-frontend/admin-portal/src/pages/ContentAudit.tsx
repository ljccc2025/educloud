import { useEffect, useState } from 'react';
import { CheckCircle, XCircle, FileText, Video, Presentation, Eye } from 'lucide-react';
import DataTable, { type Column } from '../components/DataTable';
import AuditModal from '../components/AuditModal';
import { contentApi } from '../services/api';
import type { ContentItem, ContentType, CourseStatus } from '../types';
import { cn } from '../utils/cn';

type Tab = 'PENDING' | 'APPROVED' | 'REJECTED';

const tabConfig: { key: Tab; label: string }[] = [
  { key: 'PENDING', label: '待审核' },
  { key: 'APPROVED', label: '已通过' },
  { key: 'REJECTED', label: '已驳回' },
];

const typeIcon: Record<ContentType, typeof FileText> = {
  VIDEO: Video,
  PDF: FileText,
  PPT: Presentation,
};

const typeBadge: Record<ContentType, string> = {
  VIDEO: 'badge-indigo',
  PDF: 'badge-red',
  PPT: 'badge-amber',
};

const typeLabel: Record<ContentType, string> = {
  VIDEO: '视频',
  PDF: 'PDF',
  PPT: 'PPT',
};

const statusBadge: Record<CourseStatus, { cls: string; text: string }> = {
  PENDING: { cls: 'badge-amber', text: '待审核' },
  APPROVED: { cls: 'badge-green', text: '已通过' },
  REJECTED: { cls: 'badge-red', text: '已驳回' },
};

export default function ContentAudit() {
  const [tab, setTab] = useState<Tab>('PENDING');
  const [items, setItems] = useState<ContentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<ContentItem | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  const load = (status: Tab) => {
    setLoading(true);
    contentApi.getList(status).then((data) => {
      setItems(data);
      setLoading(false);
    });
  };

  useEffect(() => {
    load(tab);
  }, [tab]);

  const handleQuickApprove = async (item: ContentItem) => {
    await contentApi.audit(item.id, true);
    load(tab);
  };

  const handleQuickReject = async (item: ContentItem) => {
    setSelected(item);
    setModalOpen(true);
  };

  const handleModalReject = async (reason: string) => {
    if (selected) {
      await contentApi.audit(selected.id, false);
      setModalOpen(false);
      load(tab);
    }
  };

  const handleModalApprove = async () => {
    if (selected) {
      await contentApi.audit(selected.id, true);
      setModalOpen(false);
      load(tab);
    }
  };

  const openDetail = (item: ContentItem) => {
    setSelected(item);
    setModalOpen(true);
  };

  const columns: Column<ContentItem>[] = [
    {
      key: 'title',
      header: '课件名称',
      render: (item) => {
        const Icon = typeIcon[item.type];
        return (
          <div className="flex items-center gap-3">
            <span className="flex items-center justify-center w-9 h-9 bg-ink-50 text-ink-600 shrink-0">
              <Icon size={16} />
            </span>
            <div>
              <div className="font-medium text-ink-900">{item.title}</div>
              <div className="text-xs text-ink-400">{item.fileSize}</div>
            </div>
          </div>
        );
      },
    },
    {
      key: 'type',
      header: '类型',
      render: (item) => <span className={typeBadge[item.type]}>{typeLabel[item.type]}</span>,
    },
    {
      key: 'courseName',
      header: '所属课程',
      render: (item) => <span className="text-ink-600 max-w-[200px] truncate block">{item.courseName}</span>,
    },
    { key: 'uploader', header: '上传者', render: (item) => <span className="text-ink-600">{item.uploader}</span> },
    {
      key: 'uploadDate',
      header: '上传时间',
      sortable: true,
      sortValue: (item) => item.uploadDate,
      render: (item) => <span className="text-ink-500">{item.uploadDate}</span>,
    },
    {
      key: 'status',
      header: '状态',
      render: (item) => (
        <span className={statusBadge[item.status].cls}>{statusBadge[item.status].text}</span>
      ),
    },
    {
      key: 'actions',
      header: '操作',
      align: 'right',
      render: (item) => (
        <div className="flex items-center justify-end gap-1">
          <button onClick={() => openDetail(item)} className="btn-ghost" title="查看">
            <Eye size={15} />
          </button>
          {item.status === 'PENDING' && (
            <>
              <button
                onClick={() => void handleQuickApprove(item)}
                className="btn-ghost text-green-600 hover:text-green-700"
                title="通过"
              >
                <CheckCircle size={15} />
              </button>
              <button
                onClick={() => void handleQuickReject(item)}
                className="btn-ghost text-red-500 hover:text-red-600"
                title="驳回"
              >
                <XCircle size={15} />
              </button>
            </>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="animate-fade-up opacity-0">
        <div className="section-label mb-2">内容审核</div>
        <h1 className="display-heading text-3xl md:text-4xl">内容审核</h1>
        <p className="text-ink-500 mt-2">审核教师上传的课件、视频与文档资源</p>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-1 border-b border-ink-200 animate-fade-up opacity-0 animation-delay-100">
        {tabConfig.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={cn(
              'px-5 py-3 text-sm font-medium border-b-2 -mb-px transition-colors',
              tab === t.key
                ? 'border-amber-600 text-indigo-800'
                : 'border-transparent text-ink-500 hover:text-ink-800',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="animate-fade-up opacity-0 animation-delay-200">
        <DataTable
          columns={columns}
          data={items}
          keyExtractor={(item) => item.id}
          loading={loading}
          emptyText="暂无待审核内容"
        />
      </div>

      <AuditModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onApprove={handleModalApprove}
        onReject={handleModalReject}
        title={selected?.title ?? ''}
        item={selected}
      />
    </div>
  );
}
