package com.ptr5409.wglib.biomereplace.sub;

/**
 * 功能：子群系匹配条件
 */
@FunctionalInterface
public interface SubCriterion {
    boolean matches(SubBiomeContext context);
}
