package com.kenai.dao;

import com.kenai.dataobject.ItemStockDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ItemStockDOMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table item_stock
     *
     * @mbg.generated Thu Dec 17 19:29:27 CST 2020
     */
    int deleteByPrimaryKey(Integer id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table item_stock
     *
     * @mbg.generated Thu Dec 17 19:29:27 CST 2020
     */
    int insert(ItemStockDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table item_stock
     *
     * @mbg.generated Thu Dec 17 19:29:27 CST 2020
     */
    int insertSelective(ItemStockDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table item_stock
     *
     * @mbg.generated Thu Dec 17 19:29:27 CST 2020
     */
    ItemStockDO selectByPrimaryKey(Integer id);

    /**
     * 通过itemId获取库存数据信息
     * @param itemId
     * @return
     */
    ItemStockDO selectByItemId(Integer itemId);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table item_stock
     *
     * @mbg.generated Thu Dec 17 19:29:27 CST 2020
     */
    int updateByPrimaryKeySelective(ItemStockDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table item_stock
     *
     * @mbg.generated Thu Dec 17 19:29:27 CST 2020
     */
    int updateByPrimaryKey(ItemStockDO record);

    /**
     * 减库存
     * 返回值未int类型，指影响的条目数
     */
    int decreaseStock(Integer itemId, Integer amount);
}