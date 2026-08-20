package com.educloud.common.id;

/** Generates globally unique positive identifiers while its backing lease remains valid. */
public interface IdentifierGenerator {

    long nextId();
}
