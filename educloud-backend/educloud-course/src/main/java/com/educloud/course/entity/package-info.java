/**
 * Course 实体层。
 *
 * <p>时间字段统一使用 {@link java.time.LocalDateTime} 对齐 V001__course.sql
 * 的 DATETIME(3) 本地时间语义（file 模块实体用 {@link java.time.Instant} 为既有差异，
 * 本模块不复用该差异，统一 LocalDateTime）。</p>
 */
package com.educloud.course.entity;
