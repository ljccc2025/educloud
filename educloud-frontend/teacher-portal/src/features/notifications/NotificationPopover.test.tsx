import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import NotificationPopover from './NotificationPopover';
import {
  initialTeacherNotifications,
  useTeacherNotificationStore,
} from './useTeacherNotificationStore';

const resetStore = () => {
  localStorage.clear();
  useTeacherNotificationStore.setState({
    notifications: initialTeacherNotifications.map((notification) => ({ ...notification })),
  });
};

const renderPopover = () => render(
  <MemoryRouter
    initialEntries={['/']}
    future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
  >
    <Routes>
      <Route path="/" element={<NotificationPopover />} />
      <Route path="/assignments" element={<p>作业批改页面</p>} />
      <Route path="/notifications" element={<p>全部通知页面</p>} />
    </Routes>
  </MemoryRouter>,
);

describe('NotificationPopover', () => {
  beforeEach(resetStore);

  it('显示未读数字并可通过铃铛和 Escape 开关最近通知', async () => {
    const user = userEvent.setup();
    renderPopover();

    const trigger = screen.getByRole('button', { name: '通知中心，4 条未读消息' });
    expect(screen.getByText('4')).toBeInTheDocument();

    await user.click(trigger);
    expect(screen.getByRole('dialog', { name: '最近通知' })).toBeInTheDocument();
    expect(screen.getByText('有新的作业提交')).toBeInTheDocument();
    expect(screen.queryByText('平台维护通知')).not.toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog', { name: '最近通知' })).not.toBeInTheDocument();
  });

  it('在小屏幕将面板锚定到视口并在桌面恢复铃铛右对齐', async () => {
    const user = userEvent.setup();
    renderPopover();

    await user.click(screen.getByRole('button', { name: '通知中心，4 条未读消息' }));

    expect(screen.getByRole('dialog', { name: '最近通知' })).toHaveClass(
      'fixed',
      'left-4',
      'right-4',
      'sm:absolute',
      'sm:left-auto',
      'sm:right-0',
    );
  });

  it('打开时聚焦通知面板并在 Escape 关闭后将焦点还给铃铛', async () => {
    const user = userEvent.setup();
    renderPopover();

    const trigger = screen.getByRole('button', { name: '通知中心，4 条未读消息' });
    await user.click(trigger);

    expect(screen.getByRole('dialog', { name: '最近通知' })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole('button', { name: '全部标记为已读' })).toHaveFocus();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog', { name: '最近通知' })).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it('可以从面板将全部通知标记为已读', async () => {
    const user = userEvent.setup();
    renderPopover();

    await user.click(screen.getByRole('button', { name: '通知中心，4 条未读消息' }));
    await user.click(screen.getByRole('button', { name: '全部标记为已读' }));

    expect(screen.getByRole('button', { name: '通知中心，没有未读消息' })).toBeInTheDocument();
    expect(screen.queryByText('4')).not.toBeInTheDocument();
  });

  it('打开业务通知时先标记已读再跳转', async () => {
    const user = userEvent.setup();
    renderPopover();

    await user.click(screen.getByRole('button', { name: '通知中心，4 条未读消息' }));
    await user.click(screen.getByRole('button', { name: /有新的作业提交/ }));

    expect(screen.getByText('作业批改页面')).toBeInTheDocument();
    expect(
      useTeacherNotificationStore.getState().notifications
        .find(({ id }) => id === 'assignment-submission-1')?.read,
    ).toBe(true);
  });

  it('可以从面板进入完整通知页面', async () => {
    const user = userEvent.setup();
    renderPopover();

    await user.click(screen.getByRole('button', { name: '通知中心，4 条未读消息' }));
    await user.click(screen.getByRole('button', { name: '查看全部通知' }));

    expect(screen.getByText('全部通知页面')).toBeInTheDocument();
  });
});
