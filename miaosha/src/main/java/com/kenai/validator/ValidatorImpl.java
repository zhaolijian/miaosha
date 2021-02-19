package com.kenai.validator;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Set;

/**
 * 实现InitializingBean方法的作用：在实例化bean之后会执行afterPropertiesSet()方法
 */
@Component
public class ValidatorImpl implements InitializingBean {
    @Resource
    private Validator validator;

    /**
     * 实现校验方法并返回校验结果
     * @throws Exception
     */
    public ValidationResult validate(Object bean){
        ValidationResult result = new ValidationResult();
        // 若bean中的一些参数规则有违背了ConstraintViolation定义的annotation，则会放到set集合中
        Set<ConstraintViolation<Object>> constraintViolationSet = validator.validate(bean);
        // 有错误
        if(constraintViolationSet.size() > 0){
            result.setHasErrors(true);
            constraintViolationSet.forEach(constraintViolation->{
                // 错误信息
                String errMsg = constraintViolation.getMessage();
                // 对应错误的字段
                String propertyName = constraintViolation.getPropertyPath().toString();
                result.getErrorMsgMap().put(propertyName, errMsg);
            });
        }
        return result;
    }



    @Override
    public void afterPropertiesSet() throws Exception {

    }


}
