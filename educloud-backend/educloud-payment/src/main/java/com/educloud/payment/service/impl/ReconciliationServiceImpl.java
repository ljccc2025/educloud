package com.educloud.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.PageResponse;
import com.educloud.payment.dto.request.ReconcileDiffResolveRequest;
import com.educloud.payment.dto.response.ReconciliationBatchResponse;
import com.educloud.payment.dto.response.ReconciliationDiffResponse;
import com.educloud.payment.entity.PaymentOrderEntity;
import com.educloud.payment.entity.ReconciliationBatchEntity;
import com.educloud.payment.entity.ReconciliationDiffEntity;
import com.educloud.payment.enums.DiffType;
import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.enums.ReconciliationBatchStatus;
import com.educloud.payment.enums.ResolveAction;
import com.educloud.payment.enums.ResolveStatus;
import com.educloud.payment.exception.PaymentBizException;
import com.educloud.payment.exception.PaymentErrorCode;
import com.educloud.payment.mapper.PaymentOrderMapper;
import com.educloud.payment.mapper.ReconciliationBatchMapper;
import com.educloud.payment.mapper.ReconciliationDiffMapper;
import com.educloud.payment.service.ReconciliationService;
import com.educloud.payment.spi.PaymentChannelFactory;
import com.educloud.payment.spi.PaymentChannelPlugin;
import com.educloud.payment.spi.model.ChannelBillItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final ReconciliationBatchMapper batchMapper;
    private final ReconciliationDiffMapper diffMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentChannelFactory channelFactory;

    @Override
    @Transactional
    public ReconciliationBatchResponse runReconciliation(LocalDate date, PaymentChannel channel) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(channel, "channel");

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(23, 59, 59, 999999999);
        LocalDateTime now = LocalDateTime.now();

        String batchNo = "REC_" + channel.name() + "_" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "_" + System.currentTimeMillis();
        ReconciliationBatchEntity batch = ReconciliationBatchEntity.builder()
                .id(IdWorker.getId())
                .batchNo(batchNo)
                .reconcileDate(date)
                .channelCode(channel)
                .totalCount(0)
                .totalAmountCents(0L)
                .diffCount(0)
                .status(ReconciliationBatchStatus.RUNNING)
                .startedAt(now)
                .build();
        batchMapper.insert(batch);

        List<PaymentOrderEntity> localOrders = paymentOrderMapper.selectList(
                new LambdaQueryWrapper<PaymentOrderEntity>()
                        .eq(PaymentOrderEntity::getChannelCode, channel)
                        .ge(PaymentOrderEntity::getCreatedAt, start)
                        .le(PaymentOrderEntity::getCreatedAt, end)
                        .eq(PaymentOrderEntity::getDeleted, 0));

        PaymentChannelPlugin plugin = channelFactory.getPlugin(channel);
        List<ChannelBillItem> channelBills = plugin.downloadBill(date);
        if (channelBills == null) {
            channelBills = List.of();
        }

        Map<Long, PaymentOrderEntity> localOrderMap = new HashMap<>();
        for (PaymentOrderEntity order : localOrders) {
            localOrderMap.put(order.getId(), order);
        }

        Map<Long, ChannelBillItem> channelBillMap = new HashMap<>();
        for (ChannelBillItem bill : channelBills) {
            if (bill.getPaymentOrderId() != null) {
                channelBillMap.put(bill.getPaymentOrderId(), bill);
            }
        }

        List<ReconciliationDiffEntity> diffList = new ArrayList<>();

        // 1. 本地记录比对
        for (PaymentOrderEntity local : localOrders) {
            ChannelBillItem bill = channelBillMap.get(local.getId());
            if (bill == null) {
                // 本地有但渠道无（如果是 SUCCESS 状态则是本地单边）
                if (local.getStatus() == PaymentStatus.SUCCESS) {
                    diffList.add(createDiff(batch.getId(), DiffType.LOCAL_MORE, local.getId(),
                            local.getChannelTradeNo(), local.getAmountCents(), 0L,
                            local.getStatus().name(), "NONE", now));
                }
            } else {
                // 两边均有，核对金额与状态
                if (!Objects.equals(local.getAmountCents(), bill.getAmountCents())) {
                    diffList.add(createDiff(batch.getId(), DiffType.AMOUNT_MISMATCH, local.getId(),
                            bill.getChannelTradeNo(), local.getAmountCents(), bill.getAmountCents(),
                            local.getStatus().name(), bill.getStatus().name(), now));
                } else if (local.getStatus() != bill.getStatus()) {
                    diffList.add(createDiff(batch.getId(), DiffType.STATUS_MISMATCH, local.getId(),
                            bill.getChannelTradeNo(), local.getAmountCents(), bill.getAmountCents(),
                            local.getStatus().name(), bill.getStatus().name(), now));
                }
            }
        }

        // 2. 渠道记录比对（渠道有但本地无 ➔ 渠道单边）
        for (ChannelBillItem bill : channelBills) {
            if (bill.getPaymentOrderId() == null || !localOrderMap.containsKey(bill.getPaymentOrderId())) {
                diffList.add(createDiff(batch.getId(), DiffType.CHANNEL_MORE, bill.getPaymentOrderId(),
                        bill.getChannelTradeNo(), 0L, bill.getAmountCents(),
                        "NONE", bill.getStatus().name(), now));
            }
        }

        for (ReconciliationDiffEntity diff : diffList) {
            diffMapper.insert(diff);
        }

        long totalLocalAmount = localOrders.stream()
                .mapToLong(PaymentOrderEntity::getAmountCents)
                .sum();

        batch.setTotalCount(localOrders.size() + channelBills.size());
        batch.setTotalAmountCents(totalLocalAmount);
        batch.setDiffCount(diffList.size());
        batch.setStatus(diffList.isEmpty() ? ReconciliationBatchStatus.MATCHED : ReconciliationBatchStatus.DIFF_FOUND);
        batch.setFinishedAt(LocalDateTime.now());
        batchMapper.updateById(batch);

        log.info("Reconciliation batch {} completed: totalCount={}, diffCount={}, status={}",
                batchNo, batch.getTotalCount(), batch.getDiffCount(), batch.getStatus());

        return toBatchResponse(batch);
    }

    @Override
    @Transactional
    public ReconciliationDiffResponse resolveDiff(Long adminUserId, Long diffId, ReconcileDiffResolveRequest request) {
        Objects.requireNonNull(diffId, "diffId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.getAction(), "action");

        ReconciliationDiffEntity diff = diffMapper.selectById(diffId);
        if (diff == null) {
            throw new PaymentBizException(PaymentErrorCode.RECONCILIATION_DIFF_NOT_FOUND, "对账差错单不存在");
        }

        if (diff.getResolveStatus() != ResolveStatus.UNRESOLVED) {
            throw new PaymentBizException(PaymentErrorCode.RECONCILIATION_DIFF_ALREADY_RESOLVED, "该差错单已平账处理");
        }

        LocalDateTime now = LocalDateTime.now();
        diff.setResolveAction(request.getAction());
        diff.setResolveRemark(request.getRemark());
        diff.setResolvedBy(adminUserId);
        diff.setResolvedAt(now);
        diff.setResolveStatus(request.getAction() == ResolveAction.IGNORE ? ResolveStatus.IGNORED : ResolveStatus.RESOLVED);

        diffMapper.updateById(diff);

        // 检查当前批次是否所有差错单都已处理完毕
        Long unresolvedCount = diffMapper.selectCount(
                new LambdaQueryWrapper<ReconciliationDiffEntity>()
                        .eq(ReconciliationDiffEntity::getBatchId, diff.getBatchId())
                        .eq(ReconciliationDiffEntity::getResolveStatus, ResolveStatus.UNRESOLVED));

        if (unresolvedCount == 0) {
            ReconciliationBatchEntity batch = batchMapper.selectById(diff.getBatchId());
            if (batch != null && batch.getStatus() == ReconciliationBatchStatus.DIFF_FOUND) {
                batch.setStatus(ReconciliationBatchStatus.RESOLVED);
                batchMapper.updateById(batch);
                log.info("Batch {} all diffs resolved, updated status to RESOLVED", batch.getId());
            }
        }

        return toDiffResponse(diff);
    }

    @Override
    public PageResponse<ReconciliationBatchResponse> listBatches(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<ReconciliationBatchEntity> pageParam = new Page<>(safePage, safeSize);
        Page<ReconciliationBatchEntity> resultPage = batchMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<ReconciliationBatchEntity>().orderByDesc(ReconciliationBatchEntity::getId));

        List<ReconciliationBatchResponse> items = resultPage.getRecords().stream()
                .map(this::toBatchResponse)
                .toList();

        return PageResponse.of(items, safePage, safeSize, resultPage.getTotal());
    }

    @Override
    public PageResponse<ReconciliationDiffResponse> listDiffs(Long batchId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<ReconciliationDiffEntity> pageParam = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<ReconciliationDiffEntity> query = new LambdaQueryWrapper<ReconciliationDiffEntity>()
                .orderByDesc(ReconciliationDiffEntity::getId);

        if (batchId != null) {
            query.eq(ReconciliationDiffEntity::getBatchId, batchId);
        }

        Page<ReconciliationDiffEntity> resultPage = diffMapper.selectPage(pageParam, query);
        List<ReconciliationDiffResponse> items = resultPage.getRecords().stream()
                .map(this::toDiffResponse)
                .toList();

        return PageResponse.of(items, safePage, safeSize, resultPage.getTotal());
    }

    private ReconciliationDiffEntity createDiff(
            Long batchId, DiffType diffType, Long paymentOrderId, String channelTradeNo,
            Long localAmount, Long channelAmount, String localStatus, String channelStatus, LocalDateTime now) {
        return ReconciliationDiffEntity.builder()
                .id(IdWorker.getId())
                .batchId(batchId)
                .diffType(diffType)
                .paymentOrderId(paymentOrderId)
                .channelTradeNo(channelTradeNo)
                .localAmountCents(localAmount)
                .channelAmountCents(channelAmount)
                .localStatus(localStatus)
                .channelStatus(channelStatus)
                .resolveStatus(ResolveStatus.UNRESOLVED)
                .createdAt(now)
                .build();
    }

    private ReconciliationBatchResponse toBatchResponse(ReconciliationBatchEntity entity) {
        return ReconciliationBatchResponse.builder()
                .id(entity.getId())
                .batchNo(entity.getBatchNo())
                .reconcileDate(entity.getReconcileDate())
                .channelCode(entity.getChannelCode())
                .totalCount(entity.getTotalCount())
                .totalAmountCents(entity.getTotalAmountCents())
                .diffCount(entity.getDiffCount())
                .status(entity.getStatus())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .build();
    }

    private ReconciliationDiffResponse toDiffResponse(ReconciliationDiffEntity entity) {
        return ReconciliationDiffResponse.builder()
                .id(entity.getId())
                .batchId(entity.getBatchId())
                .diffType(entity.getDiffType())
                .paymentOrderId(entity.getPaymentOrderId())
                .channelTradeNo(entity.getChannelTradeNo())
                .localAmountCents(entity.getLocalAmountCents())
                .channelAmountCents(entity.getChannelAmountCents())
                .localStatus(entity.getLocalStatus())
                .channelStatus(entity.getChannelStatus())
                .resolveStatus(entity.getResolveStatus())
                .resolveAction(entity.getResolveAction())
                .resolveRemark(entity.getResolveRemark())
                .resolvedBy(entity.getResolvedBy())
                .resolvedAt(entity.getResolvedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
