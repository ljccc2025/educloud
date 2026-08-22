import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Notifications from './Notifications';
import {
  initialTeacherNotifications,
  useTeacherNotificationStore,
} from '../features/notifications/useTeacherNotificationStore';

const resetStore = () => {
  localStorage.clear();
  useTeacherNotificationStore.setState({
    notifications: initialTeacherNotifications.map((notification) => ({ ...notification })),
  });
};

const renderPage = () => render(
  <MemoryRouter
    initialEntries={['/']}
    future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
  >
    <Routes>
      <Route path="/" element={<Notifications />} />
      <Route path="/assignments" element={<p>作业批改页面</p>} />
    </Routes>
  </MemoryRouter>,
);

describe('Notifications', () => {
  beforeEach(resetStore);

  it('默认展示全部通知及正确的消息概览', () => {
    renderPage();

    expect(screen.getByRole('heading', { name: '通知中心' })).toBeInTheDocument();
    expect(screen.getByText('有新的作业提交')).toBeInTheDocument();
    expect(screen.getByText('平台维护通知')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /全部通知 6/ })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: /未读通知 4/ })).toHaveAttribute('aria-pressed', 'false');
  });

  it('可以筛选未读通知并单独标记为已读', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: /未读通知 4/ }));
    expect(screen.queryByText('平台维护通知')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '标记「有新的作业提交」为已读' }));
    expect(screen.queryByText('有新的作业提交')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /未读通知 3/ })).toBeInTheDocument();
  });

  it('全部已读后在未读筛选中显示空状态', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: /未读通知 4/ }));
    await user.click(screen.getByRole('button', { name: '全部标记为已读' }));

    expect(screen.getByRole('heading', { name: '暂无未读通知' })).toBeInTheDocument();
    expect(screen.getByText('新的教学动态会及时出现在这里')).toBeInTheDocument();
  });

  it('没有任何通知时显示全部通知的空状态文案', () => {
    useTeacherNotificationStore.setState({ notifications: [] });

    renderPage();

    expect(screen.getByRole('heading', { name: '暂无通知' })).toBeInTheDocument();
    expect(screen.getByText('新的教学通知会及时出现在这里')).toBeInTheDocument();
  });

  it('点击业务入口时标记消息并跳转到对应页面', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: '前往批改' }));

    expect(screen.getByText('作业批改页面')).toBeInTheDocument();
    expect(
      useTeacherNotificationStore.getState().notifications
        .find(({ id }) => id === 'assignment-submission-1')?.read,
    ).toBe(true);
  });
});
