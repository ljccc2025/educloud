import { useEffect } from 'react';
import { Search, Filter, ChevronLeft, ChevronRight, Trash2, Ban, CheckCircle } from 'lucide-react';
import DataTable, { type Column } from '../components/DataTable';
import CustomSelect from '../components/CustomSelect';
import { UserAvatar } from '../components/UserAvatar';
import { useUserStore } from '../stores/useUserStore';
import type { User } from '../types';
import { cn } from '../utils/cn';

const roleBadge: Record<User['role'], string> = {
  STUDENT: 'badge-indigo',
  TEACHER: 'badge-amber',
  ADMIN: 'badge-red',
};

const roleLabel: Record<User['role'], string> = {
  STUDENT: '学员',
  TEACHER: '教师',
  ADMIN: '管理员',
};

const roleOptions = [
  { value: 'ALL', label: '全部角色' },
  { value: 'STUDENT', label: '学员' },
  { value: 'TEACHER', label: '教师' },
  { value: 'ADMIN', label: '管理员' },
];

const statusOptions = [
  { value: 'ALL', label: '全部状态' },
  { value: 'ACTIVE', label: '正常' },
  { value: 'DISABLED', label: '已禁用' },
];

export default function UserManage() {
  const {
    users,
    total,
    page,
    pageSize,
    loading,
    keyword,
    role,
    status,
    setPage,
    setKeyword,
    setRole,
    setStatus,
    fetchUsers,
    updateUserStatus,
  } = useUserStore();

  useEffect(() => {
    void fetchUsers();
  }, [fetchUsers]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  const columns: Column<User>[] = [
    { key: 'id', header: 'ID', width: '70px', sortable: true, sortValue: (r) => r.id },
    {
      key: 'user',
      header: '用户',
      render: (u) => (
        <div className="flex items-center gap-3">
          <UserAvatar name={u.nickname || u.username} src={u.avatarUrl} size="md" />
          <div>
            <div className="font-medium text-ink-900 flex items-center gap-1.5">
              <span>{u.username}</span>
              {u.nickname && <span className="text-xs text-ink-400 font-normal">({u.nickname})</span>}
            </div>
            <div className="text-xs text-ink-400">{u.phone}</div>
          </div>
        </div>
      ),
    },
    {
      key: 'email',
      header: '邮箱',
      render: (u) => <span className="text-ink-600">{u.email}</span>,
    },
    {
      key: 'role',
      header: '角色',
      render: (u) => <span className={roleBadge[u.role]}>{roleLabel[u.role]}</span>,
    },
    {
      key: 'status',
      header: '状态',
      render: (u) => (
        <span
          className={cn(
            'inline-flex items-center gap-1.5 text-xs font-medium',
            u.status === 'ACTIVE' ? 'text-green-600' : 'text-ink-400',
          )}
        >
          <span
            className={cn(
              'w-1.5 h-1.5 rounded-full',
              u.status === 'ACTIVE' ? 'bg-green-500' : 'bg-ink-300',
            )}
          />
          {u.status === 'ACTIVE' ? '正常' : '已禁用'}
        </span>
      ),
    },
    {
      key: 'registerDate',
      header: '注册日期',
      sortable: true,
      sortValue: (u) => u.registerDate,
      render: (u) => <span className="text-ink-500">{u.registerDate}</span>,
    },
    {
      key: 'actions',
      header: '操作',
      align: 'right',
      render: (u) => (
        <div className="flex items-center justify-end gap-1">
          <button
            onClick={(e) => {
              e.stopPropagation();
              void updateUserStatus(u.id, u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE');
            }}
            className={cn(
              'btn-ghost',
              u.status === 'ACTIVE' ? 'text-amber-600 hover:text-amber-700' : 'text-green-600 hover:text-green-700',
            )}
            title={u.status === 'ACTIVE' ? '禁用' : '启用'}
          >
            {u.status === 'ACTIVE' ? <Ban size={15} /> : <CheckCircle size={15} />}
            {u.status === 'ACTIVE' ? '禁用' : '启用'}
          </button>
          <button
            onClick={(e) => e.stopPropagation()}
            className="btn-ghost text-red-500 hover:text-red-600"
            title="删除"
          >
            <Trash2 size={15} />
          </button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="animate-fade-up opacity-0">
        <div className="section-label mb-2">用户中心</div>
        <h1 className="display-heading text-3xl md:text-4xl">用户管理</h1>
        <p className="text-ink-500 mt-2">管理平台所有注册用户，查看信息并调整权限状态</p>
      </div>

      {/* Filters */}
      <div className="card-editorial p-4 md:p-5 animate-fade-up opacity-0 animation-delay-100 relative z-30 overflow-visible">
        <div className="flex flex-col md:flex-row gap-3">
          <div className="relative flex-1">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" />
            <input
              type="text"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="搜索用户名、邮箱或手机号..."
              className="input-field pl-9"
            />
          </div>
          <div className="flex gap-3">
            <CustomSelect
              options={roleOptions}
              value={role}
              onChange={setRole}
              prefixIcon={Filter}
              minWidth="min-w-[140px]"
            />
            <CustomSelect
              options={statusOptions}
              value={status}
              onChange={setStatus}
              minWidth="min-w-[120px]"
            />
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="animate-fade-up opacity-0 animation-delay-200">
        <DataTable
          columns={columns}
          data={users}
          keyExtractor={(u) => u.id}
          loading={loading}
          emptyText="没有找到匹配的用户"
        />

        {/* Pagination */}
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4 mt-4 px-1">
          <div className="text-sm text-ink-500">
            共 <span className="font-medium text-ink-800">{total}</span> 条记录，第 {page} / {totalPages} 页
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage(page - 1)}
              disabled={page <= 1}
              className="btn-outline px-3 py-2 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <ChevronLeft size={16} />
              上一页
            </button>
            {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
              let p: number;
              if (totalPages <= 5) p = i + 1;
              else if (page <= 3) p = i + 1;
              else if (page >= totalPages - 2) p = totalPages - 4 + i;
              else p = page - 2 + i;
              return (
                <button
                  key={p}
                  onClick={() => setPage(p)}
                  className={cn(
                    'w-10 h-10 rounded-xl text-sm font-medium border transition-colors',
                    p === page
                      ? 'bg-brand-500 text-white border-brand-500 shadow-glow-purple'
                      : 'bg-surface border-ink-300 text-ink-600 hover:border-brand-500 hover:text-brand-500 dark:hover:text-brand-400',
                  )}
                >
                  {p}
                </button>
              );
            })}
            <button
              onClick={() => setPage(page + 1)}
              disabled={page >= totalPages}
              className="btn-outline px-3 py-2 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              下一页
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
