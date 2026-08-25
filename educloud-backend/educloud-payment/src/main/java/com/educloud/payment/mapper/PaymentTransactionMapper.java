package com.educloud.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.payment.entity.PaymentTransactionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentTransactionMapper extends BaseMapper<PaymentTransactionEntity> {
}
