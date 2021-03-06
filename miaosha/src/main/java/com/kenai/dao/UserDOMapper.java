package com.kenai.dao;
import com.kenai.dataobject.UserDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserDOMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_info
     *
     * @mbg.generated Tue Dec 15 15:27:34 CST 2020
     */
    int deleteByPrimaryKey(Integer id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_info
     *
     * @mbg.generated Tue Dec 15 15:27:34 CST 2020
     */
    int insert(UserDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_info
     *
     * @mbg.generated Tue Dec 15 15:27:34 CST 2020
     */
    int insertSelective(UserDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_info
     *
     * @mbg.generated Tue Dec 15 15:27:34 CST 2020
     */
    UserDO selectByPrimaryKey(Integer id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_info
     *
     * @mbg.generated Tue Dec 15 15:27:34 CST 2020
     */
    int updateByPrimaryKeySelective(UserDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_info
     *
     * @mbg.generated Tue Dec 15 15:27:34 CST 2020
     */
    int updateByPrimaryKey(UserDO record);

    /**
     * 根据手机号获取用户信息
     * @param telephone
     * @return
     */
    UserDO selectByTelephone(String telephone);
}