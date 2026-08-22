package com.educloud.file.service;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * 单测用事务管理器：不绑定任何资源，但完整驱动 Spring 事务同步生命周期
 * （commit → afterCommit + afterCompletion(COMMITTED)；rollback → afterCompletion(ROLLED_BACK)），
 * 使 {@code TransactionSynchronizationManager.registerSynchronization(...).afterCommit()}
 * 的回调路径可在纯单元测试中验证（等价于 ResourcelessTransactionManager 的角色）。
 */
public class TestTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // 无资源事务：begin 无操作
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        // 无资源事务：commit 无操作
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        // 无资源事务：rollback 无操作
    }
}
