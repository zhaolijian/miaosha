package com.kenai.controller.viewobject;

import lombok.Data;

/**
 * 该model用来给前端用户展示
 */
@Data
public class UserVO {
    private Integer id;
    private String name;
    private Byte gender;
    private Integer age;
    private String telephone;
}
