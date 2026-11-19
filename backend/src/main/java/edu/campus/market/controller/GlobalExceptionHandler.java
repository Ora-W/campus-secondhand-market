package edu.campus.market.controller;
import edu.campus.market.common.ApiResult;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class GlobalExceptionHandler {
 @ExceptionHandler(Exception.class)
 public ApiResult<?> handle(Exception e){return ApiResult.fail(e.getMessage()==null?"系统异常":e.getMessage());}
}
